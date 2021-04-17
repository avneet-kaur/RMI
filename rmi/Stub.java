package rmi;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;

import common.CommonUtil;

/**
 * RMI stub factory.
 * 
 * <p>
 * RMI stubs hide network communication with the remote server and provide a
 * simple object-like interface to their users. This class provides methods for
 * creating stub objects dynamically, when given pre-defined interfaces.
 * 
 * <p>
 * The network address of the remote server is set when a stub is created, and
 * may not be modified afterwards. Two stubs are equal if they implement the
 * same interface and carry the same remote server address - and would therefore
 * connect to the same skeleton. Stubs are serializable.
 */
public abstract class Stub {
	/**
	 * Creates a stub, given a skeleton with an assigned adress.
	 * 
	 * <p>
	 * The stub is assigned the address of the skeleton. The skeleton must either
	 * have been created with a fixed address, or else it must have already been
	 * started.
	 * 
	 * <p>
	 * This method should be used when the stub is created together with the
	 * skeleton. The stub may then be transmitted over the network to enable
	 * communication with the skeleton.
	 * 
	 * @param c        A <code>Class</code> object representing the interface
	 *                 implemented by the remote object.
	 * @param skeleton The skeleton whose network address is to be used.
	 * @return The stub created.
	 * @throws IllegalStateException If the skeleton has not been assigned an
	 *                               address by the user and has not yet been
	 *                               started.
	 * @throws UnknownHostException  When the skeleton address is a wildcard and a
	 *                               port is assigned, but no address can be found
	 *                               for the local host.
	 * @throws NullPointerException  If any argument is <code>null</code>.
	 * @throws Error                 If <code>c</code> does not represent a remote
	 *                               interface - an interface in which each method
	 *                               is marked as throwing
	 *                               <code>RMIException</code>, or if an object
	 *                               implementing this interface cannot be
	 *                               dynamically created.
	 */
	public static <T> T create(Class<T> c, Skeleton<T> skeleton) throws UnknownHostException {
		return create(c, skeleton.getAddress());
	}

	/**
	 * Creates a stub, given a skeleton with an assigned address and a hostname
	 * which overrides the skeleton's hostname.
	 * 
	 * <p>
	 * The stub is assigned the port of the skeleton and the given hostname. The
	 * skeleton must either have been started with a fixed port, or else it must
	 * have been started to receive a system-assigned port, for this method to
	 * succeed.
	 * 
	 * <p>
	 * This method should be used when the stub is created together with the
	 * skeleton, but firewalls or private networks prevent the system from
	 * automatically assigning a valid externally-routable address to the skeleton.
	 * In this case, the creator of the stub has the option of obtaining an
	 * externally-routable address by other means, and specifying this hostname to
	 * this method.
	 * 
	 * @param c        A <code>Class</code> object representing the interface
	 *                 implemented by the remote object.
	 * @param skeleton The skeleton whose port is to be used.
	 * @param hostname The hostname with which the stub will be created.
	 * @return The stub created.
	 * @throws IllegalStateException If the skeleton has not been assigned a port.
	 * @throws NullPointerException  If any argument is <code>null</code>.
	 * @throws Error                 If <code>c</code> does not represent a remote
	 *                               interface - an interface in which each method
	 *                               is marked as throwing
	 *                               <code>RMIException</code>, or if an object
	 *                               implementing this interface cannot be
	 *                               dynamically created.
	 */
	public static <T> T create(Class<T> c, Skeleton<T> skeleton, String hostname) {
		if (CommonUtil.isNull(hostname)) {
			throw new NullPointerException("[Stub]: Hostname is null");
		}
		return create(c, new InetSocketAddress(hostname, skeleton.getPort()));
	}

	/**
	 * Creates a stub, given the address of a remote server.
	 * 
	 * <p>
	 * This method should be used primarily when bootstrapping RMI. In this case,
	 * the server is already running on a remote host but there is not necessarily a
	 * direct way to obtain an associated stub.
	 * 
	 * @param c       A <code>Class</code> object representing the interface
	 *                implemented by the remote object.
	 * @param address The network address of the remote skeleton.
	 * @return The stub created.
	 * @throws NullPointerException If any argument is <code>null</code>.
	 * @throws Error                If <code>c</code> does not represent a remote
	 *                              interface - an interface in which each method is
	 *                              marked as throwing <code>RMIException</code>, or
	 *                              if an object implementing this interface cannot
	 *                              be dynamically created.
	 */
	public static <T> T create(Class<T> c, InetSocketAddress address) {
		// checking remote interface
		for (Method m : c.getMethods()) {
			Class[] methodExceptions = m.getExceptionTypes();
			if (!Arrays.asList(methodExceptions).contains(RMIException.class)) {
				throw new Error("[Stub]: Interface: " + c.getName() + "does not represent a remote interface.");
			}
		}
		if (CommonUtil.isNull(address)) {
			throw new NullPointerException("[Stub]: The network address of the remote skeleton is null");
		}

		Object proxyInstance;

		proxyInstance = Proxy.newProxyInstance(c.getClassLoader(), new Class[] { c },
				new StubDynamicInvocationHandler(c, address));
		return (T) proxyInstance;
	}

	private static class StubDynamicInvocationHandler implements InvocationHandler, Serializable {

		private Class<?> remoteInterface;
		private InetSocketAddress remoteAddress;

		StubDynamicInvocationHandler(Class<?> remoteInterface, InetSocketAddress remoteAddress) {
			this.remoteAddress = remoteAddress;
			this.remoteInterface = remoteInterface;
		}

		private boolean isRemote(Method method) {
			for (Method methodName : this.remoteInterface.getMethods()) {
				if (method.getName().equals(methodName.getName())
						&& Arrays.deepEquals(method.getParameterTypes(), methodName.getParameterTypes())) {
					return true;
				}
			}
			return false;
		}

		// args - an array of objects containing the values of
		// the arguments passed in the method invocation on the proxy instance,
		// or null if the method takes no arguments

		// Returns true if the argument (args[0])
		// is an instance of a dynamic proxy class
		// and this invocation handler is equal to the invocation handler of that
		// argument, and returns false otherwise.

//		 Two stubs are considered equal if they implement the same remote interface
//		 and connect to the same skeleton. The equals and hashCode methods must respect
//		 this requirement. The toString method should report the name of the remote interface 
//		 implemented by the stub, and the remote address (including hostname and port) of the skeleton to 
//		 which the stub connects. Stubs must also be serializable.
		private Object invokeLocalMethod(Method method, Object[] args) {
			if (CommonUtil.isNull(args)) {
				args = new Object[0];
			}
			if (method.getName().equals("hashCode")) {
				if (args.length != 0) {
					throw new Error("[Stub]: Arguments is given for Hashcode method");
				}
				return this.remoteAddress.hashCode();
			} else if (method.getName().equals("equals")) {
				if (args.length != 1) {
					throw new Error("[Stub]: No object is passed as an argument to compare with.");
				}
				// check if args[0] is an instance of dynamic proxy class
				// check if invocation Handler of method is equal to invocation handler of
				// that argument
				final Object proxy = args[0];
				if (CommonUtil.isNull(proxy))
					return false;

				StubDynamicInvocationHandler stubHandler;
				// return invocation handler for proxy specified
				stubHandler = (StubDynamicInvocationHandler) Proxy.getInvocationHandler(proxy);

				// two stubs are equal if they implements same remote interface and connect to
				// same sekelton
				if (!this.remoteInterface.equals(stubHandler.remoteInterface)) {
					return false;
				}
				// checking if invocation handler of method is equal to invocation handler of
				// argument
				return this.remoteAddress.equals(stubHandler.remoteAddress);

			} else {
				// The toString method should report the name of the remote interface
//				 implemented by the stub, and the remote address  of the skeleton to 
//				 which the stub connects. 
				if (!method.getName().equals("toString")) {
					throw new NoSuchMethodError("[Stub]: Method= " + method.getName() + " is not implemented in Stub");
				}
				if (args.length != 0) {
					throw new Error("[Stub]: Arguments is given for toString method");
				}
				return this.remoteInterface.getName() + " stub for " + this.remoteAddress.toString();
			}

		}

		/*
		 * This method Process method invocation on proxy instance.
		 */
		@Override
		public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {

			if (!this.isRemote(method)) {
				return this.invokeLocalMethod(method, args);
			}
			Socket clientSocket = null;
			// ObjectInputStream and ObjectOutputStream include passing objects
			// between hosts using a socket stream or
			// for marshaling and unmarshaling arguments and parameters
			// in a remote communication system.

			ObjectOutputStream objectOutputStream = null;
			ObjectInputStream objectInputStream = null;
			boolean boolean1;
			Object object;
			try {
				// connect client to server
				try {
					clientSocket = new Socket();
					clientSocket.connect(this.remoteAddress);
				} catch (Exception ex) {
					throw new RMIException("[Stub]: Failed to connect to to Skeleton Server", ex);
				}
				// create output stream and flush it
				try {
					objectOutputStream = new ObjectOutputStream(clientSocket.getOutputStream());
					objectOutputStream.flush();
				} catch (Exception ex2) {
					throw new RMIException("[Stub]: Failed to create object output stream", ex2);
				}

				// create input stream
				try {
					objectInputStream = new ObjectInputStream(clientSocket.getInputStream());
				} catch (Exception ex3) {
					throw new RMIException("[Stub]: Failed to create object input stream", ex3);
				}

				// send message from client to server
				try {
					objectOutputStream.writeObject(method.getName());
				} catch (Exception ex4) {
					this.readResponseFromServer(new RMIException(
							"[Stub]: Failed to write name of method to Skeleton Server" + method.getName(), ex4),
							objectInputStream, objectOutputStream);
				}

				// send parameter types to remote host server
				try {
					objectOutputStream.writeObject(method.getParameterTypes());
				} catch (Exception ex5) {
					this.readResponseFromServer(new RMIException(
							"[Stub]: Failed to write parameter types for method to Skeleton Server." + method.getName(),
							ex5), objectInputStream, objectOutputStream);
				}

				// write arguments to method
				try {
					objectOutputStream.writeObject(args);
				} catch (Exception ex6) {
					this.readResponseFromServer(new RMIException(
							"[Stub]: Failed to write arguments for method to Skeleton Server." + method.getName(), ex6),
							objectInputStream, objectOutputStream);
				}

				// flush out the output stream of sending/ writing message to pipeline
				try {
					objectOutputStream.flush();
				} catch (Exception ex7) {
					this.readResponseFromServer(
							new RMIException(
									"[Stub]: Failed to flush the output stream while calling." + method.getName(), ex7),
							objectInputStream, objectOutputStream);
				}

				// read receive tag from remote server
				try {
					boolean1 = objectInputStream.readBoolean();
				}
				// exception will be thrown if boolean is false then handle that e
				catch (Exception ex8) {
					throw new RMIException("[Stub]: Failed to read boolean result from Skeleton Server.", ex8);
				}

				// read result received from remote host
				try {
					object = objectInputStream.readObject();
				} catch (Exception ex9) {
					throw new RMIException("[Stub]: Failed to read result from Skeleton Server using ObjectInputStream",
							ex9);
				}
			} finally {
				// flush and close output stream
				if (!CommonUtil.isNull(objectOutputStream)) {
					try {
						objectOutputStream.flush();
					} catch (Exception ex10) {
					}
					try {
						objectOutputStream.close();
					} catch (Exception ex11) {
					}
				}
				// close input stream
				if (!CommonUtil.isNull(objectInputStream)) {
					try {
						objectInputStream.close();
					} catch (Exception ex12) {
					}
				}

				// close socket
				if (!CommonUtil.isNull(clientSocket)) {
					try {
						clientSocket.close();
					} catch (Exception ex13) {
					}
				}
			}
			// if we received message then return object
			if (boolean1) {
				return object;
			}

			// the throw statement to throw an object that describes an exception
			// syntax :- throw throwable;
			// The object identified by throwable is an instance of Throwable or any of its
			// subclasses.
			throw (Throwable) object;

		}

		private void readResponseFromServer(final RMIException RMIexception, final ObjectInputStream objectInputStream,
				final ObjectOutputStream objectOutputStream) throws RMIException {

			try {
				objectOutputStream.flush();
			} catch (Exception ex) {
			}
			try {
				// Handling exception thrown from Skeleton Server side
				if (objectInputStream.readBoolean()) {
					throw RMIexception;
				}
				// reading RMIException thrown from Skeleton server side
				throw (RMIException) objectInputStream.readObject();
			} catch (Exception ex1) {
				throw RMIexception;
			}
		}

	}
}
