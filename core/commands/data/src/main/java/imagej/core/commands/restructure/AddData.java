/*
 * #%L
 * ImageJ software for multidimensional image processing and analysis.
 * %%
 * Copyright (C) 2009 - 2012 Board of Regents of the University of
 * Wisconsin-Madison, Broad Institute of MIT and Harvard, and Max Planck
 * Institute of Molecular Cell Biology and Genetics.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 * The views and conclusions contained in the software and documentation are
 * those of the authors and should not be interpreted as representing official
 * policies, either expressed or implied, of any organization.
 * #L%
 */

package imagej.core.commands.restructure;

import imagej.Cancelable;
import imagej.command.DynamicCommand;
import imagej.data.Dataset;
import imagej.menu.MenuConstants;
import imagej.module.DefaultModuleItem;
import imagej.module.ItemIO;
import imagej.plugin.Menu;
import imagej.plugin.Parameter;
import imagej.plugin.Plugin;

import java.util.ArrayList;

import net.imglib2.img.ImgPlus;
import net.imglib2.meta.Axes;
import net.imglib2.meta.AxisType;
import net.imglib2.type.numeric.RealType;

/**
 * Adds hyperplanes of data to an input Dataset along a user specified axis.
 * 
 * @author Barry DeZonia
 */
@Plugin(menu = {
	@Menu(label = MenuConstants.IMAGE_LABEL, weight = MenuConstants.IMAGE_WEIGHT,
		mnemonic = MenuConstants.IMAGE_MNEMONIC),
	@Menu(label = "Data", mnemonic = 'd'), @Menu(label = "Add Data...") },
	headless = true, initializer = "initAll")
public class AddData extends DynamicCommand implements Cancelable {

	// -- Constants --

	private static final String AXIS_NAME = "axisName";
	private static final String POSITION = "position";
	private static final String QUANTITY = "quantity";

	// -- Parameters --

	@Parameter(type = ItemIO.BOTH)
	private Dataset dataset;

	@Parameter(label = "Axis to modify", persist = false,
		callback = "parameterChanged")
	private String axisName;

	@Parameter(label = "Insertion position", persist = false,
		callback = "parameterChanged")
	private long position = 1;

	@Parameter(label = "Insertion quantity", persist = false,
		callback = "parameterChanged")
	private long quantity = 1;

	// -- instance variables that are not Parameters --
	private String err;

	// -- Cancelable methods --

	@Override
	public boolean isCanceled() {
		return err != null;
	}

	@Override
	public String getCancelReason() {
		return err;
	}
	
	// -- AddData methods --

	public Dataset getDataset() {
		return dataset;
	}

	public void setDataset(final Dataset dataset) {
		this.dataset = dataset;
	}

	public AxisType getAxis() {
		return Axes.get(axisName);
	}

	public void setAxis(final AxisType axis) {
		axisName = axis.toString();
	}

	public long getPosition() {
		return position;
	}

	public void setPosition(final long position) {
		this.position = position;
	}

	public long getQuantity() {
		return quantity;
	}

	public void setQuantity(final long quantity) {
		this.quantity = quantity;
	}

	// -- Runnable methods --

	/**
	 * Creates new ImgPlus data copying pixel values as needed from an input
	 * Dataset. Assigns the ImgPlus to the input Dataset.
	 */
	@Override
	public void run() {
		final AxisType axis = Axes.get(axisName);
		if (inputBad(axis)) return;
		final AxisType[] axes = dataset.getAxes();
		final long[] newDimensions =
			RestructureUtils.getDimensions(dataset, axis, quantity);
		final ImgPlus<? extends RealType<?>> dstImgPlus =
			RestructureUtils.createNewImgPlus(dataset, newDimensions, axes);
		fillNewImgPlus(dataset.getImgPlus(), dstImgPlus, axis);
		final int compositeChannelCount =
			compositeStatus(dataset, dstImgPlus, axis);
		dstImgPlus.setCompositeChannelCount(compositeChannelCount);
		RestructureUtils.allocateColorTables(dstImgPlus);
		if (Axes.isXY(axis)) {
			RestructureUtils.copyColorTables(dataset.getImgPlus(), dstImgPlus);
		}
		else {
			final ColorTableRemapper remapper =
				new ColorTableRemapper(new RemapAlgorithm());
			remapper.remapColorTables(dataset.getImgPlus(), dstImgPlus);
		}
		// TODO - metadata, etc.?
		dataset.setImgPlus(dstImgPlus);
	}

	// -- Initializers --

	protected void initAll() {
		initAxisName();
		initPosition();
		initQuantity();
	}

	// -- Callbacks --

	protected void parameterChanged() {
		setPositionRange();
		setQuantityRange();
		clampPosition();
		clampQuantity();
	}

	// -- Helper methods --

	/**
	 * Detects if user specified data is invalid
	 */
	private boolean inputBad(final AxisType axis) {
		// axis not determined by dialog
		if (axis == null) {
			err = "Axis must not be null.";
			return true;
		}

		// setup some working variables
		final int axisIndex = dataset.getAxisIndex(axis);
		final long axisSize = dataset.getImgPlus().dimension(axisIndex);

		// axis not present in Dataset
		if (axisIndex < 0) {
			err = "Axis "+axis.getLabel()+" is not present in input dataset.";
			return true;
		}

		// bad value for startPosition
		if (position < 1 || position > axisSize + 1) {
			err = "Insertion position is out of bounds.";
			return true;
		}

		// bad value for numAdding
		if (quantity <= 0 || (quantity > Long.MAX_VALUE - axisSize)) {
			err = "Insertion quantity is out of bounds.";
			return true;
		}

		// if here everything is okay
		return false;
	}

	/**
	 * Fills the newly created ImgPlus with data values from a smaller source
	 * image. Copies data from existing hyperplanes.
	 */
	private void
		fillNewImgPlus(final ImgPlus<? extends RealType<?>> srcImgPlus,
			final ImgPlus<? extends RealType<?>> dstImgPlus,
			final AxisType modifiedAxis)
	{
		final long[] dimensions = dataset.getDims();
		final int axisIndex = dataset.getAxisIndex(modifiedAxis);
		final long axisSize = dimensions[axisIndex];
		final long numBeforeInsert = position - 1; // one-based position
		final long numInInsertion = quantity;
		final long numAfterInsertion = axisSize - numBeforeInsert;

		RestructureUtils.copyData(srcImgPlus, dstImgPlus, modifiedAxis, 0, 0,
			numBeforeInsert);
		RestructureUtils.copyData(srcImgPlus, dstImgPlus, modifiedAxis,
			numBeforeInsert, numBeforeInsert + numInInsertion, numAfterInsertion);
	}

	private int compositeStatus(final Dataset origData,
		final ImgPlus<?> dstImgPlus, final AxisType axis)
	{

		// adding along non-channel axis
		if (axis != Axes.CHANNEL) {
			return origData.getCompositeChannelCount();
		}

		// else adding hyperplanes along channel axis

		// calc working data
		final int currComposCount = dataset.getCompositeChannelCount();
		final int origAxisPos = origData.getAxisIndex(Axes.CHANNEL);
		final long numOrigChannels = origData.getImgPlus().dimension(origAxisPos);
		final long numNewChannels = dstImgPlus.dimension(origAxisPos);

		// was "composite" on 1 channel
		if (currComposCount == 1) {
			return 1;
		}

		// was composite on all channels
		if (numOrigChannels == currComposCount) {
			return (int) numNewChannels; // in future be composite on all channels
		}

		// was composite on a subset of channels that divides channels evenly
		if (numOrigChannels % currComposCount == 0 &&
			numNewChannels % currComposCount == 0)
		{
			return currComposCount;
		}

		// cannot figure out a good count - no longer composite
		return 1;
	}

	private class RemapAlgorithm implements ColorTableRemapper.RemapAlgorithm {

		@Override
		public boolean isValidSourcePlane(final long i) {
			return true;
		}

		@Override
		public void remapPlanePosition(final long[] origPlaneDims,
			final long[] origPlanePos, final long[] newPlanePos)
		{
			final AxisType axis = Axes.get(axisName);
			final int axisIndex = dataset.getAxisIndex(axis);
			for (int i = 0; i < origPlanePos.length; i++) {
				if (i != axisIndex - 2) {
					newPlanePos[i] = origPlanePos[i];
				}
				else {
					if (origPlanePos[i] < position - 1) newPlanePos[i] = origPlanePos[i];
					else newPlanePos[i] = origPlanePos[i] + quantity;
				}
			}
		}
	}

	private void initAxisName() {
		@SuppressWarnings("unchecked")
		final DefaultModuleItem<String> axisNameItem =
			(DefaultModuleItem<String>) getInfo().getInput(AXIS_NAME);
		final AxisType[] axes = getDataset().getAxes();
		final ArrayList<String> choices = new ArrayList<String>();
		for (final AxisType a : axes) {
			choices.add(a.getLabel());
		}
		axisNameItem.setChoices(choices);
	}

	private void initPosition() {
		final long max = getDataset().getImgPlus().dimension(0);
		setItemRange(POSITION, 1, max);
		setPosition(1);
	}

	private void initQuantity() {
		setItemRange(QUANTITY, 1, Long.MAX_VALUE);
		setQuantity(1);
	}

	private void setPositionRange() {
		final long dimLen = currDimLen();
		setItemRange(POSITION, 1, dimLen + 1);
	}

	private void setQuantityRange() {
		final long max = Long.MAX_VALUE - getPosition() + 1;
		setItemRange(QUANTITY, 1, max);
	}

	private void clampPosition() {
		final long max = currDimLen() + 1;
		final long pos = getPosition();
		if (pos < 1) setPosition(1);
		else if (pos > max) setPosition(max);
	}

	private void clampQuantity() {
		final long max = Long.MAX_VALUE - getPosition() + 1;
		final long total = getQuantity();
		if (total < 1) setQuantity(1);
		else if (total > max) setQuantity(max);
	}

	private long currDimLen() {
		final AxisType axis = getAxis();
		final int axisIndex = getDataset().getAxisIndex(axis);
		return getDataset().getImgPlus().dimension(axisIndex);
	}

	private void setItemRange(final String fieldName, final long min,
		@SuppressWarnings("unused") final long max)
	{
		@SuppressWarnings("unchecked")
		final DefaultModuleItem<Long> item =
			(DefaultModuleItem<Long>) getInfo().getInput(fieldName);
		item.setMinimumValue(min);
		// TODO - disable until we fix ticket #886
		// item.setMaximumValue(max);
	}

}