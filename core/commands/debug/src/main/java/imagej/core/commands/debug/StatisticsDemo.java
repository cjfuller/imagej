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

package imagej.core.commands.debug;

import net.imglib2.ops.pointset.HyperVolumePointSet;
import net.imglib2.ops.pointset.PointSet;
import net.imglib2.ops.pointset.RoiPointSet;
import imagej.command.Command;
import imagej.data.Dataset;
import imagej.data.display.ImageDisplay;
import imagej.data.display.OverlayService;
import imagej.data.measure.StatisticsService;
import imagej.data.overlay.Overlay;
import imagej.event.StatusService;
import imagej.plugin.Parameter;
import imagej.plugin.Plugin;
import imagej.widget.Button;

/**
 * Shows how to use the StatisticsService.
 * 
 * @author Barry DeZonia
 *
 */
@Plugin(menuPath = "Plugins>Sandbox>Statistics Demo")
public class StatisticsDemo implements Command {

	// -- Parameters --
	
	@Parameter
	private StatisticsService statSrv;
	
	@Parameter
	private OverlayService oSrv;
	
	@Parameter
	private StatusService sSrv;
	
	@Parameter
	private ImageDisplay display;
	
	@Parameter
	private Dataset dataset;
	
	@Parameter(label="Measure mean", callback = "mean")
	private Button mean;
	
	@Parameter(label="Measure min", callback = "min")
	private Button min;
	
	@Parameter(label="Measure max", callback = "max")
	private Button max;

	@Parameter(label="Measure median", callback = "median")
	private Button median;

	@Parameter(label="Measure midpoint", callback = "midpoint")
	private Button midpoint;

	private PointSet getRegion() { 
		Overlay overlay = oSrv.getActiveOverlay(display);
		if (overlay != null) {
			return new RoiPointSet(overlay.getRegionOfInterest());
		}
		long[] dims = display.getDims();
		// 1st plane only
		for (int i = 2; i < dims.length; i++) {
			dims[i] = 1;
		}
		return new HyperVolumePointSet(dims);
	}
	
	// -- Command methods --
	
	@Override
	public void run() {
		// does nothing. the whole of this plugin is the interactivity of the button
		// presses with updates to the status line.
	}

	// -- StatisticsDemo methods --
	
	protected void mean() {
		display("Mean", statSrv.arithmeticMean(dataset, getRegion()));
	}
	
	protected void min() {
		display("Min", statSrv.minimum(dataset, getRegion()));
	}
	
	protected void max() {
		display("Max", statSrv.maximum(dataset, getRegion()));
	}
	
	protected void median() {
		display("Median", statSrv.median(dataset, getRegion()));
	}
	
	protected void midpoint() {
		display("Midpoint", statSrv.midpoint(dataset, getRegion()));
	}
	
	// -- private helpers --

	private void display(String funcName, double value) {
		sSrv.showStatus(funcName+" of selected region is "+value);
	}
}