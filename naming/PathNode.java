package naming;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import common.Path;
import rmi.RMIException;
import storage.Command;
import storage.Storage;

class ServerStubs {
	private Storage storageStub;
	private Command commandStub;

	public ServerStubs(Storage s, Command c) {
		this.storageStub = s;
		this.commandStub = c;
	}

	// get storage stub for client
	public Storage getStorage() {
		return storageStub;
	}

	// get command stub for Naming server
	public Command getCommand() {
		return commandStub;
	}

	// check duplicacy of stubs
	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;

		ServerStubs that = (ServerStubs) o;

		return this.storageStub.equals(((ServerStubs) o).getStorage())
				&& this.commandStub.equals(((ServerStubs) o).getCommand());
	}

	@Override
	public int hashCode() {
		int result = storageStub.hashCode();
		result = 31 * result + commandStub.hashCode();

		return result;
	}
}

class PathNode {
	private Path nodePath;
	HashMap<String, PathNode> childNodes;
	String name;
	public Vector<ServerStubs> serverStubs;
	private ServerStubs serverStub;

	public PathNode(String n) {
		childNodes = new HashMap<String, PathNode>();
		name = n;
		serverStubs = new Vector<ServerStubs>();
	}

	public PathNode(String n, ServerStubs s) {
		childNodes = new HashMap<String, PathNode>();
		name = n;
		this.serverStubs = new Vector<ServerStubs>();
		this.serverStubs.add(s);
		this.serverStub = s;
	}

	public synchronized String getName() {
		return name;
	}

	public synchronized HashMap<String, PathNode> getChildren() {
		return childNodes;
	}

	public void addChild(String component, PathNode child) throws UnsupportedOperationException {
		if (isFile())
			throw new UnsupportedOperationException("Unable to add child to a leaf node");

		if (childNodes.containsKey(component))
			throw new UnsupportedOperationException("Unable to add an existing node again");

		childNodes.put(component, child);
	}

	public void deleteChild(String component) throws UnsupportedOperationException {
		if (!childNodes.containsKey(component))
			throw new UnsupportedOperationException("Unable to delete a non-existing node");

		childNodes.remove(component);
	}

	public synchronized void removeChild(Path path) {
		childNodes.remove(path.toString());
	}

	public synchronized boolean isFile() {
		return serverStub != null;
	}

	public PathNode getNodeByPath(Path path) throws FileNotFoundException {
		PathNode curNode = this;

		for (String component : path) {
			if (!curNode.childNodes.containsKey(component))
				throw new FileNotFoundException("Unable to get node from path");

			curNode = curNode.childNodes.get(component);
		}
		return curNode;
	}

	public synchronized ServerStubs getServer() {
		return serverStub;
	}

	public synchronized List<ServerStubs> getAllStorage() {
		return serverStubs;
	}

	public String[] getChildrenList(PathNode currentNode) {

		String[] listOfChildren = new String[childNodes.size()];

		int i = 0;
		for (String childName : currentNode.getChildren().keySet()) {
			listOfChildren[i] = childName;
			i++;
		}
		return listOfChildren;
	}

	public void deleteNode(Path path) throws RMIException {
		PathNode node = null;
		try {
			node = getNodeByPath(path);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		for (ServerStubs ss : serverStubs) {
			Command commandStub = serverStub.getCommand();
			commandStub.delete(path);
		}

		try {
			node.getNodeByPath(path.parent()).removeChild(path);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

}
