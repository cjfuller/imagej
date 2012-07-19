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

package imagej.updater.ui;

import imagej.log.LogService;
import imagej.updater.core.Dependency;
import imagej.updater.core.FileObject;
import imagej.updater.core.FileObject.Action;
import imagej.updater.core.FileObject.Status;
import imagej.updater.core.FilesCollection;
import imagej.updater.core.FilesCollection.Filter;
import imagej.updater.core.FilesCollection.UpdateSite;
import imagej.updater.core.FilesUploader;
import imagej.updater.core.Installer;
import imagej.updater.util.Downloadable;
import imagej.updater.util.Downloader;
import imagej.updater.util.Progress;
import imagej.updater.util.StderrLogService;
import imagej.updater.util.StderrProgress;
import imagej.updater.util.UpdaterUserInterface;
import imagej.updater.util.Util;
import imagej.util.FileUtils;

import java.awt.Frame;
import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

/**
 * This is the command-line interface into the ImageJ Updater.
 * 
 * @author Johannes Schindelin
 */
public class CommandLine {

	protected static LogService log = new StderrLogService();
	protected FilesCollection files;
	protected Progress progress;

	public CommandLine() throws IOException, ParserConfigurationException,
		SAXException
	{
		progress = new StderrProgress(80);
		files = new FilesCollection(log, FileUtils.getBaseDirectory());
		String warnings = files.downloadIndexAndChecksum(progress);
		if (!warnings.equals("")) System.err.println(warnings);
	}

	protected class FileFilter implements Filter {

		protected Set<String> fileNames;

		public FileFilter(final List<String> files) {
			if (files != null && files.size() > 0) {
				fileNames = new HashSet<String>();
				for (final String file : files)
					fileNames.add(Util.stripPrefix(file, ""));
			}
		}

		@Override
		public boolean matches(final FileObject file) {
			if (!file.isUpdateablePlatform()) return false;
			if (fileNames != null && !fileNames.contains(file.filename)) return false;
			return file.getStatus() != Status.OBSOLETE_UNINSTALLED;
		}
	}

	public void listCurrent(final List<String> list) {
		for (final FileObject file : files.filter(new FileFilter(list)))
			System.out.println(file.filename + "-" + file.getTimestamp());
	}

	public void list(final List<String> list, Filter filter) {
		if (filter == null) filter = new FileFilter(list);
		else filter = files.and(new FileFilter(list), filter);
		files.sort();
		for (final FileObject file : files.filter(filter))
			System.out.println(file.filename + "\t(" + file.getStatus() + ")\t" +
				file.getTimestamp());
	}

	public void list(final List<String> list) {
		list(list, null);
	}

	public void listUptodate(final List<String> list) {
		list(list, files.is(Status.INSTALLED));
	}

	public void listNotUptodate(final List<String> list) {
		list(list, files.not(files.oneOf(new Status[] { Status.OBSOLETE,
			Status.INSTALLED, Status.LOCAL_ONLY })));
	}

	public void listUpdateable(final List<String> list) {
		list(list, files.is(Status.UPDATEABLE));
	}

	public void listModified(final List<String> list) {
		list(list, files.is(Status.MODIFIED));
	}

	class OneFile implements Downloadable {

		FileObject file;

		OneFile(final FileObject file) {
			this.file = file;
		}

		@Override
		public File getDestination() {
			return files.prefix(file.filename);
		}

		@Override
		public String getURL() {
			return files.getURL(file);
		}

		@Override
		public long getFilesize() {
			return file.filesize;
		}

		@Override
		public String toString() {
			return file.filename;
		}
	}

	public void download(final FileObject file) {
		try {
			new Downloader(progress).start(new OneFile(file));
			if (file.executable && !Util.platform.startsWith("win")) try {
				Runtime.getRuntime()
					.exec(
						new String[] { "chmod", "0755",
							files.prefix(file.filename).getPath() });
			}
			catch (final Exception e) {
				e.printStackTrace();
				throw new RuntimeException("Could not mark " + file.filename +
					" as executable");
			}
			System.err.println("Installed " + file.filename);
		}
		catch (final IOException e) {
			System.err.println("IO error downloading " + file.filename + ": " +
				e.getMessage());
		}
	}

	public void delete(final FileObject file) {
		if (new File(file.filename).delete()) System.err.println("Deleted " +
			file.filename);
		else System.err.println("Failed to delete " + file.filename);
	}

	protected void addDependencies(final FileObject file,
		final Set<FileObject> all)
	{
		if (all.contains(file)) return;
		all.add(file);
		for (final Dependency dependency : file.getDependencies()) {
			final FileObject file2 = files.get(dependency.filename);
			if (file2 != null) addDependencies(file2, all);
		}
	}

	public void update(final List<String> list) {
		update(list, false);
	}

	public void update(final List<String> list, final boolean force) {
		update(list, force, false);
	}

	public void update(final List<String> list, final boolean force,
		final boolean pristine)
	{
		// Include all dependencies
		final Set<FileObject> all = new HashSet<FileObject>();
		for (final FileObject file : files.filter(new FileFilter(list)))
			addDependencies(file, all);
		for (final FileObject file : all)
			list.add(file.filename);
		try {
			for (final FileObject file : all) {
				switch (file.getStatus()) {
				case MODIFIED:
					if (!force) {
						System.err.println("Skipping locally-modified "
								+ file.filename);
						break;
					}
				case UPDATEABLE:
				case NEW:
				case NOT_INSTALLED:
					file.setFirstValidAction(files, new Action[] {
							Action.UPDATE, Action.INSTALL });
					break;
				case LOCAL_ONLY:
					if (!pristine) {
						System.err.println("Keeping local-only "
								+ file.filename);
						break;
					}
				case OBSOLETE_MODIFIED:
					if (!force) {
						System.err.println("Keeping modified but obsolete "
								+ file.filename);
						break;
					}
				case OBSOLETE:
					file.stageForUninstall(files);
					break;
				default:
					if (files != null && files.size() > 0)
						System.err.println("Not updating " + file.filename
								+ " (" + file.getStatus() + ")");
				}
			}
			Installer installer = new Installer(files, progress);
			installer.start();
			installer.moveUpdatedIntoPlace();
			files.write();
		}
		catch (final Exception e) {
			System.err.println("Could not write db.xml.gz:");
			e.printStackTrace();
		}
	}

	public void upload(final List<String> list) {
		if (list == null || list.size() == 0) die("Which files do you mean to upload?");

		String updateSite = null;
		int count = 0;
		for (final String name : list) {
			final FileObject file = files.get(name);
			if (file == null) die("No file '" + name + "' found!");
			if (file.getStatus() == Status.INSTALLED) {
				System.err.println("Skipping up-to-date " + name);
				continue;
			}
			if (file.getStatus() == Status.LOCAL_ONLY &&
				Util.isLauncher(file.filename))
			{
				file.executable = true;
				file.addPlatform(Util.platformForLauncher(file.filename));
				for (final String fileName : new String[] { "jars/ij-launcher.jar" }) {
					final FileObject dependency = files.get(fileName);
					if (dependency != null) file.addDependency(files, dependency);
				}
			}
			if (updateSite == null) {
				updateSite = file.updateSite;
				if (updateSite == null) updateSite =
					file.updateSite = chooseUploadSite(name);
				if (updateSite == null) die("Canceled");
			}
			else if (file.updateSite == null) {
				System.err.println("Uploading new file '" + name + "' to  site '" +
					updateSite + "'");
				file.updateSite = updateSite;
			}
			else if (!file.updateSite.equals(updateSite)) die("Cannot upload to multiple update sites (" +
				files.get(0) +
				" to " +
				updateSite +
				" and " +
				name +
				" to " +
				file.updateSite + ")");
			file.setAction(files, Action.UPLOAD);
			count++;
		}
		if (count == 0) {
			System.err.println("Nothing to upload");
			return;
		}
		System.err.println("Uploading to " + getLongUpdateSiteName(updateSite));

		try {
			final FilesUploader uploader = new FilesUploader(files, updateSite);
			if (!uploader.login())
				die("Login failed!");
			uploader.upload(progress);
			files.write();
		}
		catch (final Throwable e) {
			e.printStackTrace();
			die("Error during upload: " + e);
		}
	}

	public String chooseUploadSite(final String file) {
		final List<String> names = new ArrayList<String>();
		final List<String> options = new ArrayList<String>();
		for (final String name : files.getUpdateSiteNames()) {
			final UpdateSite updateSite = files.getUpdateSite(name);
			if (updateSite.uploadDirectory == null ||
				updateSite.uploadDirectory.equals("")) continue;
			names.add(name);
			options.add(getLongUpdateSiteName(name));
		}
		if (names.size() == 0) {
			System.err.println("No uploadable sites found");
			return null;
		}
		final String message = "Choose upload site for file '" + file + "'";
		final int index =
			UpdaterUserInterface.get().optionDialog(message, message,
				options.toArray(new String[options.size()]), 0);
		return index < 0 ? null : names.get(index);
	}

	public String getLongUpdateSiteName(final String name) {
		final UpdateSite site = files.getUpdateSite(name);
		return name + " (" +
			(site.sshHost == null || site.equals("") ? "" : site.sshHost + ":") +
			site.uploadDirectory + ")";
	}

	public static CommandLine getInstance() {
		try {
			return new CommandLine();
		}
		catch (final Exception e) {
			e.printStackTrace();
			System.err.println("Could not parse db.xml.gz: " + e.getMessage());
			throw new RuntimeException(e);
		}
	}

	public static List<String> makeList(final String[] list, int start) {
		final List<String> result = new ArrayList<String>();
		while (start < list.length)
			result.add(list[start++]);
		return result;
	}

	public static void die(final String message) {
		System.err.println(message);
		System.exit(1);
	}

	public static void usage() {
		System.err.println("Usage: imagej.updater.ui.CommandLine <command>\n"
			+ "\n" + "Commands:\n" + "\tlist [<files>]\n"
			+ "\tlist-uptodate [<files>]\n" + "\tlist-not-uptodate [<files>]\n"
			+ "\tlist-updateable [<files>]\n" + "\tlist-modified [<files>]\n"
			+ "\tlist-current [<files>]\n" + "\tupdate [<files>]\n"
			+ "\tupdate-force [<files>]\n" + "\tupdate-force-pristine [<files>]\n"
			+ "\tupload [<files>]");
	}

	public static void main(final String[] args) {
		if (args.length == 0) {
			usage();
			System.exit(0);
		}

		String http_proxy = System.getenv("http_proxy");
		if (http_proxy != null && http_proxy.startsWith("http://")) {
			final int colon = http_proxy.indexOf(':', 7);
			final int slash = http_proxy.indexOf('/', 7);
			int port = 80;
			if (colon < 0) http_proxy =
				slash < 0 ? http_proxy.substring(7) : http_proxy.substring(7, slash);
			else {
				port =
					Integer.parseInt(slash < 0 ? http_proxy.substring(colon + 1)
						: http_proxy.substring(colon + 1, slash));
				http_proxy = http_proxy.substring(7, colon);
			}
			System.setProperty("http.proxyHost", http_proxy);
			System.setProperty("http.proxyPort", "" + port);
		}
		else Util.useSystemProxies();
		Authenticator.setDefault(new ProxyAuthenticator());
		setUserInterface();

		final String command = args[0];
		if (command.equals("list")) getInstance().list(makeList(args, 1));
		else if (command.equals("list-current")) getInstance().listCurrent(
			makeList(args, 1));
		else if (command.equals("list-uptodate")) getInstance().listUptodate(
			makeList(args, 1));
		else if (command.equals("list-not-uptodate")) getInstance()
			.listNotUptodate(makeList(args, 1));
		else if (command.equals("list-updateable")) getInstance().listUpdateable(
			makeList(args, 1));
		else if (command.equals("list-modified")) getInstance().listModified(
			makeList(args, 1));
		else if (command.equals("update")) getInstance().update(makeList(args, 1));
		else if (command.equals("update-force")) getInstance().update(
			makeList(args, 1), true);
		else if (command.equals("update-force-pristine")) getInstance().update(
			makeList(args, 1), true, true);
		else if (command.equals("upload")) getInstance().upload(makeList(args, 1));
		else usage();
	}

	protected static class ProxyAuthenticator extends Authenticator {

		protected Console console = System.console();

		@Override
		protected PasswordAuthentication getPasswordAuthentication() {
			final String user =
				console.readLine("                                  \rProxy User: ");
			final char[] password = console.readPassword("Proxy Password: ");
			return new PasswordAuthentication(user, password);
		}
	}

	protected static void setUserInterface() {
		UpdaterUserInterface.set(new ConsoleUserInterface());
	}

	protected static class ConsoleUserInterface extends UpdaterUserInterface {

		protected Console console = System.console();
		protected int count;

		@Override
		public String getPassword(final String message) {
			System.out.print(message + ": ");
			return new String(console.readPassword());
		}

		@Override
		public boolean promptYesNo(final String title, final String message) {
			System.err.print(title + ": " + message);
			final String line = console.readLine();
			return line.startsWith("y") || line.startsWith("Y");
		}

		public void showPrompt(String prompt) {
			if (!prompt.endsWith(": ")) prompt += ": ";
			System.err.print(prompt);
		}

		public String getUsername(final String prompt) {
			showPrompt(prompt);
			return console.readLine();
		}

		public int askChoice(final String[] options) {
			for (int i = 0; i < options.length; i++)
				System.err.println("" + (i + 1) + ": " + options[i]);
			for (;;) {
				System.err.print("Choice? ");
				final String answer = console.readLine();
				if (answer.equals("")) return -1;
				try {
					return Integer.parseInt(answer) - 1;
				}
				catch (final Exception e) { /* ignore */}
			}
		}

		@Override
		public void error(final String message) {
			log.error(message);
		}

		@Override
		public void info(final String message, final String title) {
			log.info(title + ": " + message);
		}

		@Override
		public void log(final String message) {
			log.info(message);
		}

		@Override
		public void debug(final String message) {
			log.debug(message);
		}

		@Override
		public OutputStream getOutputStream() {
			return System.out;
		}

		@Override
		public void showStatus(final String message) {
			log(message);
		}

		@Override
		public void handleException(final Throwable exception) {
			exception.printStackTrace();
		}

		@Override
		public boolean isBatchMode() {
			return false;
		}

		@Override
		public int optionDialog(final String message, final String title,
			final Object[] options, final int def)
		{
			for (int i = 0; i < options.length; i++)
				System.err.println("" + (i + 1) + ") " + options[i]);
			for (;;) {
				System.out.print("Your choice (default: " + def + ": " + options[def] +
					")? ");
				final String line = console.readLine();
				if (line.equals("")) return def;
				try {
					final int option = Integer.parseInt(line);
					if (option > 0 && option <= options.length) return option - 1;
				}
				catch (final NumberFormatException e) {
					// ignore
				}
			}
		}

		@Override
		public String getPref(final String key) {
			return null;
		}

		@Override
		public void setPref(final String key, final String value) {
			log("Ignoring setting '" + key + "'");
		}

		@Override
		public void savePreferences() { /* do nothing */}

		@Override
		public void openURL(final String url) throws IOException {
			log("Please open " + url + " in your browser");
		}

		@Override
		public String getString(final String title) {
			System.out.print(title + ": ");
			return console.readLine();
		}

		@Override
		public void addWindow(final Frame window) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void removeWindow(final Frame window) {
			throw new UnsupportedOperationException();
		}
	}
}