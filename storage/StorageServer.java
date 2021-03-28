package storage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import common.CommonUtil;
import common.Path;
import naming.Registration;
import rmi.RMIException;
import rmi.Skeleton;
import rmi.Stub;

/**
 * Storage server.
 * 
 * <p>
 * Storage servers respond to client file access requests. The files accessible
 * through a storage server are those accessible under a given directory of the
 * local filesystem.
 */
public class StorageServer implements Storage, Command {
	File root;
	storageSkeleton storSkeleton;
	commandSkeleton commSkeleton;

	boolean storageStopped = false;
	boolean commandStopped = false;

	private class storageSkeleton extends Skeleton<Storage> {
		StorageServer storageServer;

		public storageSkeleton(Class<Storage> c, Storage server, StorageServer ss) {
			super(c, server);
			storageServer = ss;
		}

		public storageSkeleton(Class<Storage> c, Storage server, InetSocketAddress address, StorageServer ss) {
			super(c, server, address);
			storageServer = ss;
		}

		protected void stopped(Throwable cause) {
			synchronized (storageSkeleton.this) {
				storageStopped = true;
				if (commandStopped && storageStopped) {
					storageServer.stopped(null);
				}

			}
		}
	}

	private class commandSkeleton extends Skeleton<Command> {
		StorageServer storageServer;

		public commandSkeleton(Class<Command> c, Command server, StorageServer ss) {
			super(c, server);
			storageServer = ss;
		}

		public commandSkeleton(Class<Command> c, Command server, InetSocketAddress address, StorageServer ss) {
			super(c, server, address);
			storageServer = ss;
		}

		protected void stopped(Throwable cause) {
			synchronized (commandSkeleton.this) {
				commandStopped = true;
				if (commandStopped && storageStopped) {
					storageServer.stopped(null);
				}

			}
		}
	}

	/**
	 * Creates a storage server, given a directory on the local filesystem.
	 * 
	 * @param root Directory on the local filesystem. The contents of this directory
	 *             will be accessible through the storage server.
	 * @throws NullPointerException If <code>root</code> is <code>null</code>.
	 */
	public StorageServer(File root) {

		int storagePort = 0, commandPort = 0;
		if (CommonUtil.isNull(root)) {
			throw new NullPointerException("Root cannot be null.");
		}
		storSkeleton = new storageSkeleton(Storage.class, this, new InetSocketAddress(storagePort), this);

		commSkeleton = new commandSkeleton(Command.class, this, new InetSocketAddress(commandPort), this);

		// The files hosted by a Storage Server are stored in its local file system in a
		// directory denoted as temporary directory.
		this.root = root.getAbsoluteFile();

	}

	/**
	 * Starts the storage server and registers it with the given naming server.
	 * 
	 * @param hostname      The externally-routable hostname of the local host on
	 *                      which the storage server is running. This is used to
	 *                      ensure that the stub which is provided to the naming
	 *                      server by the <code>start</code> method carries the
	 *                      externally visible hostname or address of this storage
	 *                      server.
	 * @param naming_server Remote interface for the naming server with which the
	 *                      storage server is to register.
	 * @throws UnknownHostException  If a stub cannot be created for the storage
	 *                               server because a valid address has not been
	 *                               assigned.
	 * @throws FileNotFoundException If the directory with which the server was
	 *                               created does not exist or is in fact a file.
	 * @throws RMIException          If the storage server cannot be started, or if
	 *                               it cannot be registered.
	 */
	public synchronized void start(String hostname, Registration naming_server)
			throws RMIException, UnknownHostException, FileNotFoundException {

		if (!root.exists() || !root.isDirectory()) {
			throw new FileNotFoundException(
					"Directory with which the server was created does not exist or is in fact a file");
		}

		storSkeleton.start();
		commSkeleton.start();

		// registers storage server with the given naming server

		// During registration, the Storage Server recursively lists the contents of its
		// temporary directory and sends the list of local paths (of files only) along
		// with its stubs to the Naming Server. The Naming Server maps the received
		// local paths to DFS paths and sends back a list of duplicate files for
		// deletion. Duplicate files are files that have been already registered and
		// therefore exist in the Naming Server's directory tree.

		Path[] listOfDuplicateFilesForDeletion = naming_server.register(
				Stub.create(Storage.class, storSkeleton, hostname), Stub.create(Command.class, commSkeleton, hostname),
				Path.list(root));

		for (int i = 0; i < listOfDuplicateFilesForDeletion.length; i++) {
			delete(listOfDuplicateFilesForDeletion[i]);
		}

//		After the storage server has deleted the files as commanded, it must prune
//		 its directory tree by removing all directories under which no files can be
//		 found. This includes, for example, directories which contain only empty
//		 directories.
		pruneEmptyDirectories(root);

	}

	/**
	 * Stops the storage server.
	 * 
	 * <p>
	 * The server should not be restarted.
	 */
	public void stop() {
		storSkeleton.stop();
		commSkeleton.stop();
	}

	/**
	 * Called when the storage server has shut down.
	 * 
	 * @param cause The cause for the shutdown, if any, or <code>null</code> if the
	 *              server was shut down by the user's request.
	 */
	protected void stopped(Throwable cause) {
	}

	/**
	 * Returns the length of a file, in bytes.
	 * 
	 * @param file Path to the file.
	 * @return The length of the file.
	 * @throws FileNotFoundException If the file cannot be found or the path refers
	 *                               to a directory.
	 * @throws RMIException          If the call cannot be completed due to a
	 *                               network error.
	 */
	@Override
	public synchronized long size(Path file) throws FileNotFoundException {
		File tmp = file.toFile(root);
		if (!tmp.exists() || tmp.isDirectory()) {
			throw new FileNotFoundException("File cannot be found or the path refers to a directory");
		}
		return tmp.length();
	}

	/**
	 * Reads a sequence of bytes from a file.
	 * 
	 * @param file   Path to the file.
	 * @param offset Offset into the file to the beginning of the sequence.
	 * @param length The number of bytes to be read.
	 * @return An array containing the bytes read. If the call succeeds, the number
	 *         of bytes read is equal to the number of bytes requested.
	 * @throws IndexOutOfBoundsException If the sequence specified by
	 *                                   <code>offset</code> and <code>length</code>
	 *                                   is outside the bounds of the file, or if
	 *                                   <code>length</code> is negative.
	 * @throws FileNotFoundException     If the file cannot be found or the path
	 *                                   refers to a directory.
	 * @throws IOException               If the file read cannot be completed on the
	 *                                   server.
	 * @throws RMIException              If the call cannot be completed due to a
	 *                                   network error.
	 */

	@Override
	public synchronized byte[] read(Path file, long offset, int length) throws FileNotFoundException, IOException {
		// convert path to file
		File tFile = file.toFile(root);
		if (!tFile.exists() || tFile.isDirectory()) {
			throw new FileNotFoundException("file cannot be found or the path refers to a directory");
		}

		if (offset < 0 || offset > Integer.MAX_VALUE || length < 0 || offset + length > tFile.length()) {
			throw new IndexOutOfBoundsException(
					"Sequence specified by offset and length is outside the bounds of the file, or length is negative.");
		}

		FileInputStream i = new FileInputStream(tFile);
		i.getChannel().position(offset);
		byte[] buffer = new byte[length];
		i.read(buffer);
		i.close();

		return buffer;
	}

	/**
	 * Writes bytes to a file.
	 * 
	 * @param file   Path to the file.
	 * @param offset Offset into the file where data is to be written.
	 * @param data   Array of bytes to be written.
	 * @throws IndexOutOfBoundsException If <code>offset</code> is negative.
	 * @throws FileNotFoundException     If the file cannot be found or the path
	 *                                   refers to a directory.
	 * @throws IOException               If the file write cannot be completed on
	 *                                   the server.
	 * @throws RMIException              If the call cannot be completed due to a
	 *                                   network error.
	 */
	@Override
	public synchronized void write(Path file, long offset, byte[] data) throws FileNotFoundException, IOException {
		if (offset < 0) {
			throw new IndexOutOfBoundsException("offset is negative");
		}
		File tmpFile = file.toFile(root);

		if (!tmpFile.exists() || tmpFile.isDirectory()) {
			throw new FileNotFoundException("file cannot be found or the path refers to a directory");
		}

		if (!tmpFile.canWrite()) {
			throw new IOException("File write cannot be completed on the server");
		}

		FileOutputStream o = new FileOutputStream(tmpFile);
		o.getChannel().position(offset);
		try {
			o.write(data);
		} catch (IOException e) {
			throw new IOException(e);
		}
		o.flush();
		o.close();

	}

	/**
	 * Creates a file on the storage server.
	 * 
	 * @param file Path to the file to be created. The parent directory will be
	 *             created if it does not exist. This path may not be the root
	 *             directory.
	 * @return <code>true</code> if the file is created; <code>false</code> if it
	 *         cannot be created.
	 * @throws RMIException If the call cannot be completed due to a network error.
	 */
	// The following methods are documented in Command.java.
	@Override
	public synchronized boolean create(Path file) {

		// Attempt to call create with null as argument.
		if (CommonUtil.isNull(file)) {
			throw new NullPointerException();
		}

		// Attempt to call create with root directory as argument.
		if (file.isRoot()) {
			return false;
		}

		File createFile = file.toFile(root);
		// Attempt to call create with existing file as argument.
		if (createFile.exists()) {
			return false;
		}

		// You can ensure that parent directories exist by using this method
		// File#mkdirs().
		createFile.getParentFile().mkdirs();
		try {
			return createFile.createNewFile();
		} catch (IOException e1) {
			e1.printStackTrace();
			return false;
		}

	}

	/**
	 * Deletes a file or directory on the storage server.
	 * 
	 * <p>
	 * If the file is a directory and cannot be deleted, some, all, or none of its
	 * contents may be deleted by this operation.
	 * 
	 * @param path Path to the file or directory to be deleted. The root directory
	 *             cannot be deleted.
	 * @return <code>true</code> if the file or directory is deleted;
	 *         <code>false</code> otherwise.
	 * @throws RMIException If the call cannot be completed due to a network error.
	 */
	@Override
	public synchronized boolean delete(Path path) {
		File fileName = path.toFile(root);

		// Attempt to call delete with root directory as argument.
		if (path.isRoot() || !fileName.exists()) {
			return false;
		}

		boolean isDeleted = del(path.toFile(root));

		pruneEmptyDirectories(fileName);

		return isDeleted;
	}

	// delete recursively
	private boolean del(File fileName) {
		if (fileName.isDirectory()) {
			for (File file : fileName.listFiles()) {
				del(file);
			}
		}
		return fileName.delete();
	}

	private synchronized void pruneEmptyDirectories(File file) {
		File parentDirectory = file.getParentFile();
		if (!CommonUtil.isNull(parentDirectory)) {
			// check if parent file is a directory, and do not have any file in directory
			// and is equal to root
			while (parentDirectory.isDirectory() && parentDirectory.listFiles().length == 0
					&& !parentDirectory.equals(root)) {
				// deletes the directory/file
				parentDirectory.delete();
				parentDirectory = parentDirectory.getParentFile();
			}
		}
	}

}
