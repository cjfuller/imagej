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

package imagej.data.lut;

import imagej.util.AppUtils;
import imagej.util.ClassUtils;
import imagej.util.FileUtils;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The LutFinder determines the locations of all .lut files known to ImageJ.
 * 
 * @author Barry DeZonia
 */
public class LutFinder {

	public static final String LUT_DIRECTORY = AppUtils.getBaseDirectory() +
		File.separator + "luts";

	/**
	 * Finds the {@link URL}s of the .lut files known to ImageJ. .lut files can
	 * reside in the standard Jar file or in the luts subdirectory of the
	 * application.
	 * 
	 * @return A collection of URLs referencing the known .lut files
	 */
	public Collection<URL> findLuts() {
		URL jarURL = getJarURL();
		URL dirURL = getDirectoryURL();
		Collection<URL> jarLutURLs = getLuts(jarURL);
		Collection<URL> dirLutURLs = getLuts(dirURL);
		HashMap<String, URL> combined = new HashMap<String, URL>();
		// do jar luts first
		putAll(jarLutURLs, combined);
		// do file luts second: user can thus override jar luts if desired
		putAll(dirLutURLs, combined);
		return combined.values();
	}

	// -- private helpers --

	private URL getJarURL() {
		return ClassUtils.getLocation(this.getClass());
	}

	private URL getDirectoryURL() {
		try {
			return new URL("file://" + LUT_DIRECTORY);
		}
		catch (MalformedURLException e) {
			return null;
		}
	}

	private Collection<URL> getLuts(URL base) {
		Collection<URL> urls = FileUtils.listContents(base);
		return filter(urls, ".*\\.lut$");
	}

	private Collection<URL> filter(Collection<URL> urlCollection, String regex) {
		ArrayList<URL> list = new ArrayList<URL>();
		Pattern p = Pattern.compile(regex);
		for (URL url : urlCollection) {
			if (p.matcher(url.toString()).matches()) list.add(url);
		}
		return list;
	}

	// this will put urls into a map. the names are determined relative to the
	// "/luts" directory. Since there is a luts dir in the app and in the jar
	// one set can overwrite the other. Above we have order such that user luts
	// can override jar luts.

	private void putAll(Collection<URL> urls, Map<String, URL> map) {
		for (URL url : urls) {
			String id = url.toString();
			int lutIndex = id.lastIndexOf("/luts/");
			if (lutIndex >= 0) id = id.substring(lutIndex, id.length());
			map.put(id, url);
		}
	}

}
