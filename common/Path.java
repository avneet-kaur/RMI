package common;

import java.io.*;
import java.util.*;

/**
 * Distributed filesystem paths.
 * 
 * <p>
 * Objects of type <code>Path</code> are used by all filesystem interfaces. Path
 * objects are immutable.
 * 
 * <p>
 * The string representation of paths is a forward-slash-delimeted sequence of
 * path components. The root directory is represented as a single forward slash.
 * 
 * <p>
 * The colon (<code>:</code>) and forward slash (<code>/</code>) characters are
 * not permitted within path components. The forward slash is the delimeter, and
 * the colon is reserved as a delimeter for application use.
 */
public class Path implements Iterable<String>, Serializable {

	private static final long serialVersionUID = -8460244722757570319L;
	private Vector<String> editablePath;

	/** Creates a new path which represents the root directory. */
	public Path() {
		editablePath = new Vector<String>();
	}

	/**
	 * Creates a new path by appending the given component to an existing path.
	 * 
	 * @param path      The existing path.
	 * @param component The new component.
	 * @throws IllegalArgumentException If <code>component</code> includes the
	 *                                  separator, a colon, or
	 *                                  <code>component</code> is the empty string.
	 */
	public Path(Path path, String component) {
		if (component == null || !isComponentLegal(component) || component.length() == 0)
			throw new IllegalArgumentException("Component is invalid.");
		Iterator<String> pathIterator = path.iterator();
		editablePath = new Vector<String>();
		while (pathIterator.hasNext()) {
			editablePath.add(pathIterator.next());
		}
		editablePath.add(component);
	}

	/**
	 * Creates a new path from a path string.
	 * 
	 * <p>
	 * The string is a sequence of components delimited with forward slashes. Empty
	 * components are dropped. The string must begin with a forward slash.
	 * 
	 * @param path The path string.
	 * @throws IllegalArgumentException If the path string does not begin with a
	 *                                  forward slash, or if the path contains a
	 *                                  colon character.
	 */
	public Path(String path) {
		if (CommonUtil.isNull(path))
			throw new IllegalArgumentException("[Path]: Path is null");

		// string must begin with a forward slash.
		if (path.length() == 0 || path.charAt(0) != '/')
			throw new IllegalArgumentException("[Path]: Path does not begin with forward slash (/).");

		editablePath = new Vector<String>();

		for (String component : path.split("/")) {
			if (!isComponentLegal(component)) {
				throw new IllegalArgumentException("[Path]: Component is invalid since it contains / or : ");
			}
			// drop empty component
			else if (component.length() == 0) {
				continue;
			} else {
				editablePath.add(component);
			}
		}
	}

	/**
	 * Returns an iterator over the components of the path.
	 * 
	 * <p>
	 * The iterator cannot be used to modify the path object - the
	 * <code>remove</code> method is not supported.
	 * 
	 * @return The iterator.
	 */
	@Override
	public Iterator<String> iterator() {
		return new Iterator<String>() {
			int current = 0;

			// Returns {@code true} if the iteration has more elements.
			@Override
			public boolean hasNext() {
				return (current < editablePath.size());
			}

			// Returns the next element in the iteration.
			@Override
			public String next() {
				String obj = "";
				if (hasNext()) {
					obj = editablePath.get(current);

				} else {
					throw new NoSuchElementException("[Path]: Trying to advance past end of iterator.");
				}
				current++;
				return obj;
			}

			public void remove() {
				throw new UnsupportedOperationException("[Path]: Remove is not supported.");
			}

		};
	}

	/**
	 * Lists the paths of all files in a directory tree on the local filesystem.
	 * 
	 * @param directory The root directory of the directory tree.
	 * @return An array of relative paths, one for each file in the directory tree.
	 * @throws FileNotFoundException    If the root directory does not exist.
	 * @throws IllegalArgumentException If <code>directory</code> exists but does
	 *                                  not refer to a directory.
	 */
	public static Path[] list(File directory) throws FileNotFoundException {
		if (CommonUtil.isNull(directory))
			throw new FileNotFoundException("[Path]: Directory does not exist");

		if (!directory.exists())
			throw new FileNotFoundException("[Path]: The root directory does not exist.");

		if (!directory.isDirectory())
			throw new IllegalArgumentException("[Path]: Test whether it is directory");

		ArrayList<Path> paths = parseDirectory(directory, directory.getPath().length());
		return paths.toArray(new Path[paths.size()]);
	}

	// return list of all path of files in a directory tree
	private static ArrayList<Path> parseDirectory(File directory, int directoryPathLength) {
		ArrayList<Path> listOfPathsofAllFiles = new ArrayList<Path>();

		for (File file : directory.listFiles()) {
			if (file.isDirectory()) {
				for (Path p : parseDirectory(file, directoryPathLength))
					listOfPathsofAllFiles.add(p);
			}
			// else if file is of type file
			else {
				listOfPathsofAllFiles.add(new Path(file.getPath().substring(directoryPathLength)));
			}
		}
		return listOfPathsofAllFiles;
	}

	/**
	 * Determines whether the path represents the root directory.
	 * 
	 * @return <code>true</code> if the path does represent the root directory, and
	 *         <code>false</code> if it does not.
	 */
	public boolean isRoot() {
		return editablePath.isEmpty();
	}

	/**
	 * Returns the path to the parent of this path.
	 * 
	 * @throws IllegalArgumentException If the path represents the root directory,
	 *                                  and therefore has no parent.
	 */
	public Path parent() {
		if (this.isRoot())
			throw new IllegalArgumentException(
					"[Path]:path represents the root directory and therefore has no parent.");

		if (editablePath.size() == 1) {
			return new Path();
		}

		String parentPath = "";
		for (int i = 0; i < editablePath.size() - 1; i++) {
			parentPath += "/" + editablePath.get(i);
		}
		// return parent path
		return new Path(parentPath);
	}

	/**
	 * Returns the last component in the path.
	 * 
	 * @throws IllegalArgumentException If the path represents the root directory,
	 *                                  and therefore has no last component.
	 */
	public String last() {
		if (this.isRoot())
			throw new IllegalArgumentException(
					"The path represent the root directory, and therefore has no last component. ");
		return editablePath.get(editablePath.size() - 1);
	}

	/**
	 * Determines if the given path is a subpath of this path.
	 * 
	 * <p>
	 * The other path is a subpath of this path if it is a prefix of this path. Note
	 * that by this definition, each path is a subpath of itself.
	 * 
	 * @param other The path to be tested.
	 * @return <code>true</code> If and only if the other path is a subpath of this
	 *         path.
	 */
	public boolean isSubpath(Path other) {
		// check if this path is the prefix of other path.
		return this.toString().startsWith(other.toString());
	}

	/**
	 * Converts the path to <code>File</code> object.
	 * 
	 * @param root The resulting <code>File</code> object is created relative to
	 *             this directory.
	 * @return The <code>File</code> object.
	 */
	public File toFile(File root) {
		if (!CommonUtil.isNull(root))
			return new File(root, this.toString());
		else
			// create a new file instance of string pathname
			return new File(this.toString());
	}

	/**
	 * Compares two paths for equality.
	 * 
	 * <p>
	 * Two paths are equal if they share all the same components.
	 * 
	 * @param other The other path.
	 * @return <code>true</code> if and only if the two paths are equal.
	 */
	@Override
	public boolean equals(Object other) {
		return this.toString().equals(other.toString());
	}

	/** Returns the hash code of the path. */
	@Override
	public int hashCode() {
		return this.toString().hashCode();
	}

	/**
	 * Converts the path to a string.
	 * 
	 * <p>
	 * The string may later be used as an argument to the <code>Path(String)</code>
	 * constructor.
	 * 
	 * @return The string representation of the path.
	 */
	@Override
	public String toString() {
		if (editablePath.size() == 0)
			return "/";

		String path = "";
		for (int i = 0; i < editablePath.size(); i++) {
			path += "/" + editablePath.get(i);
		}
		return path;
	}

	/**
	 * check if component includes the separator or a colon
	 * 
	 * @param component takes input.
	 * @return true/false if component is valid or not
	 */
	private boolean isComponentLegal(String component) {
		for (char comp : component.toCharArray())
			if (comp == '/' || comp == ':')
				return false;
		return true;
	}

}
