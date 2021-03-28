package naming;

import java.io.FileNotFoundException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Random;
import java.util.Vector;

import common.CommonUtil;
import common.Path;
import rmi.RMIException;
import rmi.Skeleton;
import storage.Command;
import storage.Storage;

/**
 * Naming server.
 * 
 * <p>
 * Each instance of the filesystem is centered on a single naming server. The
 * naming server maintains the filesystem directory tree. It does not store any
 * file data - this is done by separate storage servers. The primary purpose of
 * the naming server is to map each file name (path) to the storage server which
 * hosts the file's contents.
 * 
 * <p>
 * The naming server provides two interfaces, <code>Service</code> and
 * <code>Registration</code>, which are accessible through RMI. Storage servers
 * use the <code>Registration</code> interface to inform the naming server of
 * their existence. Clients use the <code>Service</code> interface to perform
 * most filesystem operations. The documentation accompanying these interfaces
 * provides details on the methods supported.
 * 
 * <p>
 * Stubs for accessing the naming server must typically be created by directly
 * specifying the remote network address. To make this possible, the client and
 * registration interfaces are available at well-known ports defined in
 * <code>NamingStubs</code>.
 */
public class NamingServer implements Service, Registration {

	private PathNode root;
	private Skeleton<Service> serviceSekeleton;
	private Skeleton<Registration> registrationSkeleton;

	ServerStubs serverStubs;

	private Vector<ServerStubs> serverStubsList;

	/**
	 * Creates the naming server object.
	 * 
	 * <p>
	 * The naming server is not started.
	 */
	public NamingServer() {
		root = new PathNode("");
		// to create skeleton
		// 1. an interface
		// 2. class that implements the logic of methods defined in the given interface
		// 3. Network address of the server
		serviceSekeleton = new Skeleton<Service>(Service.class, this, new InetSocketAddress(NamingStubs.SERVICE_PORT));
		registrationSkeleton = new Skeleton<Registration>(Registration.class, this,
				new InetSocketAddress(NamingStubs.REGISTRATION_PORT));
		serverStubsList = new Vector<ServerStubs>();
	}

	/**
	 * Starts the naming server.
	 * 
	 * <p>
	 * After this method is called, it is possible to access the client and
	 * registration interfaces of the naming server remotely.
	 * 
	 * @throws RMIException If either of the two skeletons, for the client or
	 *                      registration server interfaces, could not be started.
	 *                      The user should not attempt to start the server again if
	 *                      an exception occurs.
	 */
	public synchronized void start() throws RMIException {
		// start skeleton servers:- naming server
		serviceSekeleton.start();
		registrationSkeleton.start();
	}

	/**
	 * Stops the naming server.
	 * 
	 * <p>
	 * This method waits for both the client and registration interface skeletons to
	 * stop. It attempts to interrupt as many of the threads that are executing
	 * naming server code as possible. After this method is called, the naming
	 * server is no longer accessible remotely. The naming server should not be
	 * restarted.
	 */
	public void stop() {
		serviceSekeleton.stop();
		registrationSkeleton.stop();
		stopped(null);
	}

	/**
	 * Indicates that the server has completely shut down.
	 * 
	 * <p>
	 * This method should be overridden for error reporting and application exit
	 * purposes. The default implementation does nothing.
	 * 
	 * @param cause The cause for the shutdown, or <code>null</code> if the shutdown
	 *              was by explicit user request.
	 */
	protected void stopped(Throwable cause) {
	}

	/**
	 * Determines whether a path refers to a directory.
	 * 
	 * @param path The object to be checked.
	 * @return <code>true</code> if the object is a directory, <code>false</code> if
	 *         it is a file.
	 * @throws FileNotFoundException If the object specified by <code>path</code>
	 *                               cannot be found.
	 * @throws RMIException          If the call cannot be completed due to a
	 *                               network error.
	 */
	@Override
	public boolean isDirectory(Path path) throws FileNotFoundException {
		PathNode currentNode = this.root.getNodeByPath(path);
		return !currentNode.isFile();
	}

	/**
	 * Lists the contents of a directory.
	 * 
	 * @param directory The directory to be listed.
	 * @return An array of the directory entries. The entries are not guaranteed to
	 *         be in any particular order.
	 * @throws FileNotFoundException If the given path does not refer to a
	 *                               directory.
	 * @throws RMIException          If the call cannot be completed due to a
	 *                               network error.
	 */
	@Override
	public String[] list(Path directory) throws FileNotFoundException {
		if (!isDirectory(directory)) {
			throw new FileNotFoundException("given path does not refer to a directory");
		}

		PathNode currentNode = this.root;
		for (String directoryEntry : directory) {
			currentNode = currentNode.getChildren().get(directoryEntry);
		}

		String[] list = currentNode.getChildrenList(currentNode);
		return list;
	}

	/**
	 * Creates the given file, if it does not exist.
	 * 
	 * @param file Path at which the file is to be created.
	 * @return <code>true</code> if the file is created successfully,
	 *         <code>false</code> otherwise. The file is not created if a file or
	 *         directory with the given name already exists.
	 * @throws FileNotFoundException If the parent directory does not exist.
	 * @throws IllegalStateException If no storage servers are connected to the
	 *                               naming server.
	 * @throws RMIException          If the call cannot be completed due to a
	 *                               network error.
	 */
	@Override
	public boolean createFile(Path file) throws RMIException, FileNotFoundException {
		// createFile should not accept null as argument
		if (CommonUtil.isNull(file)) {
			throw new NullPointerException("CreateFile should not accept null as argument");
		}

		// Check that createFile rejects the root directory.
		if (file.isRoot()) {
			return false;
		}

		// Check that createFile rejects paths whose parent directories do not
		// exist.
		if (!isDirectory(file.parent())) {
			throw new FileNotFoundException("Path rejected because Parent directory does not exist");
		}

		// Check that createDirectory rejects paths whose parent directories are
		// in fact files.
		if (root.getNodeByPath(file.parent()).isFile()) {
			throw new FileNotFoundException("Rejects paths whose parent directories are in fact files.");
		}

		PathNode parent = root;
		PathNode currentNode;

		for (String entry : file) {
			// Check that createFile rejects paths to existing files.
			// check if particular entry from file path exist already in child nodes
			currentNode = parent.getChildren().get(entry);

			// file does not exist so create it on storage server using command stub
			if (CommonUtil.isNull(currentNode)) {
				// get command stub to create a new file in Storage server from naming server
				int randomServer = new Random().nextInt(serverStubsList.size());
				ServerStubs serverStub = serverStubsList.get(randomServer);
				serverStub.getCommand().create(file);
				parent.addChild(entry, new PathNode(entry, serverStub));
				return true;
			}
			// make the current node parent so that in next turn child node of next node
			// will be extracted.
			parent = currentNode;
		}
		return false;
	}

	/**
	 * Creates the given directory, if it does not exist.
	 * 
	 * @param directory Path at which the directory is to be created.
	 * @return <code>true</code> if the directory is created successfully,
	 *         <code>false</code> otherwise. The directory is not created if a file
	 *         or directory with the given name already exists.
	 * @throws FileNotFoundException If the parent directory does not exist.
	 * @throws RMIException          If the call cannot be completed due to a
	 *                               network error.
	 */
	@Override
	public boolean createDirectory(Path directory) throws FileNotFoundException {
		// Check that createDirectory rejects null.
		if (CommonUtil.isNull(directory)) {
			throw new NullPointerException("createDirectory rejects null as argument.");
		}

		// Check that createDirectory rejects the root directory as an
		// argument.
		if (directory.isRoot()) {
			return false;
		}
		// Check that createDirectory rejects paths whose parent directories do
		// not exist.
		if (!isDirectory(directory.parent())) {
			throw new FileNotFoundException();
		}

		// Check that createDirectory rejects paths whose parent directories are
		// in fact files.
		if (root.getNodeByPath(directory.parent()).isFile()) {
			throw new FileNotFoundException(
					"createDirectory rejects paths whose parent directories are in fact files.");
		}

		PathNode parent = root;
		PathNode currentNode;
		for (String entry : directory) {
			// Check that createDirectory rejects paths to existing directories.
			currentNode = parent.getChildren().get(entry);

			// Attempt to create the directory.
			if (CommonUtil.isNull(currentNode)) {
				parent.addChild(entry, new PathNode(entry));
				return true;
			}

			// Check that createDirectory rejects paths to existing files.
			if (currentNode.getName().equals(directory.last())) {
				return false;
			}
			parent = currentNode;
		}
		return false;
	}

	/**
	 * Deletes a file or directory.
	 * 
	 * @param path Path to the file or directory to be deleted.
	 * @return <code>true</code> if the file or directory is deleted;
	 *         <code>false</code> otherwise. The root directory cannot be deleted.
	 * @throws FileNotFoundException If the object or parent directory does not
	 *                               exist.
	 * @throws RMIException          If the call cannot be completed due to a
	 *                               network error.
	 */
	@Override
	public boolean delete(Path path) throws FileNotFoundException, RMIException {

		PathNode currentNode = this.root.getNodeByPath(path);

		if (CommonUtil.isNull(currentNode))
			throw new FileNotFoundException("Delete rejects null argument.");

		PathNode node = root.getNodeByPath(path);
		if (CommonUtil.isNull(node)) {
			throw new FileNotFoundException("Path to File/Directory does not exist");
		} else {
			root.deleteNode(path);
		}
		return true;
	}

	/**
	 * Returns a stub for the storage server hosting a file.
	 * 
	 * @param file Path to the file.
	 * @return A stub for communicating with the storage server.
	 * @throws FileNotFoundException If the file does not exist.
	 * @throws RMIException          If the call cannot be completed due to a
	 *                               network error.
	 */
	@Override
	public Storage getStorage(Path file) throws FileNotFoundException {
		PathNode currentNode = this.root.getNodeByPath(file);

		// Check that getStorage rejects null.
		if (CommonUtil.isNull(currentNode)) {
			throw new NullPointerException();
		}

		// Check that getStorage rejects directories.
		if (!currentNode.isFile()) {
			throw new FileNotFoundException();
		}
		return currentNode.getServer().getStorage();
	}

	/**
	 * Registers a storage server with the naming server.
	 * 
	 * <p>
	 * The storage server notifies the naming server of the files that it is
	 * hosting. Note that the storage server does not notify the naming server of
	 * any directories. The naming server attempts to add as many of these files as
	 * possible to its directory tree. The naming server then replies to the storage
	 * server with a subset of these files that the storage server must delete from
	 * its local storage.
	 * 
	 * <p>
	 * After the storage server has deleted the files as commanded, it must prune
	 * its directory tree by removing all directories under which no files can be
	 * found. This includes, for example, directories which contain only empty
	 * directories.
	 * 
	 * @param client_stub  Storage server client service stub. This will be given to
	 *                     clients when operations need to be performed on a file on
	 *                     the storage server.
	 * @param command_stub Storage server command service stub. This will be used by
	 *                     the naming server to issue commands that modify the
	 *                     directory tree on the storage server.
	 * @param files        The list of files stored on the storage server. This list
	 *                     is merged with the directory tree already present on the
	 *                     naming server. Duplicate filenames are dropped.
	 * @return A list of duplicate files to delete on the local storage of the
	 *         registering storage server.
	 * @throws IllegalStateException If the storage server is already registered.
	 * @throws NullPointerException  If any of the arguments is <code>null</code>.
	 * @throws RMIException          If the call cannot be completed due to a
	 *                               network error.
	 */
	// The method register is documented in Registration.java.
	@Override
	public Path[] register(Storage client_stub, Command command_stub, Path[] files) {

		// Attempt to register with null as the client interface stub.
		// Attempt to register with null as the command interface stub.
		// Attempt to register with null as the file list.
		if (CommonUtil.isNull(client_stub) || CommonUtil.isNull(command_stub) || CommonUtil.isNull(files)) {
			throw new NullPointerException();
		}

		ServerStubs newStubToRegister = new ServerStubs(client_stub, command_stub);

		// Attempt to register the storage server with the naming server a
		// second time.
		if (serverStubsList.contains(newStubToRegister)) {
			throw new IllegalStateException("Naming server accepted duplicate registration");
		}

		// add new stub to stub list
		serverStubsList.add(newStubToRegister);

		ArrayList<Path> duplicateFiles = new ArrayList<Path>();

		// loop over list of files
		for (int i = 0; i < files.length; i++) {
			PathNode currentNode = root;
			boolean isDuplicate = false;
			// loop over paths of file
			for (String entry : files[i]) {
				// check if file exists or not
				currentNode = currentNode.getChildren().get(entry);
				if (CommonUtil.isNull(currentNode)) {
					// if sub-directory does not exist, break from loop and add that file
					break;
				}
				if (entry.equals(files[i].last())) {
					// if file exists and add that existing file to duplicate file list
					isDuplicate = true;
					duplicateFiles.add(files[i]);
				}
			}

			// add sub-directory to directory tree
			PathNode parent = root;
			for (String entry : files[i]) {
				currentNode = parent.getChildren().get(entry);
				if (CommonUtil.isNull(currentNode)) {
					PathNode newNode;
					if (entry.equals(files[i].last())) {
						newNode = new PathNode(entry, newStubToRegister);
					} else {
						newNode = new PathNode(entry);
					}
					// add new subdirectory to parent
					parent.addChild(entry, newNode);
				}
				// get reference to newly added node (node added to parent)
				parent = parent.getChildren().get(entry);
			}
		}

		// A list of duplicate files to delete on the local storage of the registering
		// storage server.
		return duplicateFiles.toArray(new Path[duplicateFiles.size()]);
	}
}
