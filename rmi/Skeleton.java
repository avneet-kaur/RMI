package rmi;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;

import common.CommonUtil;

/**
 * RMI skeleton
 * 
 * <p>
 * A skeleton encapsulates a multithreaded TCP server. The server's clients are
 * intended to be RMI stubs created using the <code>Stub</code> class.
 * 
 * <p>
 * The skeleton class is parametrized by a type variable. This type variable
 * should be instantiated with an interface. The skeleton will accept from the
 * stub requests for calls to the methods of this interface. It will then
 * forward those requests to an object. The object is specified when the
 * skeleton is constructed, and must implement the remote interface. Each method
 * in the interface should be marked as throwing <code>RMIException</code>, in
 * addition to any other exceptions that the user desires.
 * 
 * <p>
 * Exceptions may occur at the top level in the listening and service threads.
 * The skeleton's response to these exceptions can be customized by deriving a
 * class from <code>Skeleton</code> and overriding <code>listen_error</code> or
 * <code>service_error</code>.
 */
public class Skeleton<T> {

	public Class<T> c;
	T server;

	private InetSocketAddress address;
	boolean started = false;
	boolean isStopped = false;

	private ServerSocket serverSocket;

	Thread listenerThread;

	/**
	 * Creates a <code>Skeleton</code> with no initial server address. The address
	 * will be determined by the system when <code>start</code> is called.
	 * Equivalent to using <code>Skeleton(null)</code>.
	 * 
	 * <p>
	 * This constructor is for skeletons that will not be used for bootstrapping RMI
	 * - those that therefore do not require a well-known port.
	 * 
	 * @param c      An object representing the class of the interface for which the
	 *               skeleton server is to handle method call requests.
	 * @param server An object implementing said interface. Requests for method
	 *               calls are forwarded by the skeleton to this object.
	 * @throws Error                If <code>c</code> does not represent a remote
	 *                              interface - an interface whose methods are all
	 *                              marked as throwing <code>RMIException</code>.
	 * @throws NullPointerException If either of <code>c</code> or
	 *                              <code>server</code> is <code>null</code>.
	 */
	public Skeleton(Class<T> c, T server) {
		this.started = false;
		this.isStopped = false;
		if (CommonUtil.isNull(c) || CommonUtil.isNull(server))
			throw new NullPointerException("[Skeleton]: Null pointer exception in Skeleton(Class<T> c, T server)");

		for (Method m : c.getMethods()) {
			Class[] methodExceptions = m.getExceptionTypes();
			if (!Arrays.asList(methodExceptions).contains(RMIException.class)) {
				throw new Error("[Skeleton]: Interface: " + c.getName() + "does not represent a remote interface.");
			}
		}
		this.c = c;
		this.server = server;
	}

	/**
	 * Creates a <code>Skeleton</code> with the given initial server address.
	 * 
	 * <p>
	 * This constructor should be used when the port number is significant.
	 * 
	 * @param c       An object representing the class of the interface for which
	 *                the skeleton server is to handle method call requests.
	 * @param server  An object implementing said interface. Requests for method
	 *                calls are forwarded by the skeleton to this object.
	 * @param address The address at which the skeleton is to run. If
	 *                <code>null</code>, the address will be chosen by the system
	 *                when <code>start</code> is called.
	 * @throws Error                If <code>c</code> does not represent a remote
	 *                              interface - an interface whose methods are all
	 *                              marked as throwing <code>RMIException</code>.
	 * @throws NullPointerException If either of <code>c</code> or
	 *                              <code>server</code> is <code>null</code>.
	 */
	public Skeleton(Class<T> c, T server, InetSocketAddress address) {
		this.started = false;
		this.isStopped = false;

		if (CommonUtil.isNull(c) || CommonUtil.isNull(server))
			throw new NullPointerException(
					"[Skeleton]: Null pointer exception in Skeleton(Class<T> c, T server, InetSocketAddress address) ");

		for (Method m : c.getMethods()) {
			Class[] methodExceptions = m.getExceptionTypes();
			if (!Arrays.asList(methodExceptions).contains(RMIException.class)) {
				throw new Error("[Skeleton]: Interface: " + c.getName() + "does not represent a remote interface.");
			}
		}
		this.c = c;
		this.server = server;
		if (CommonUtil.isNull(address))
			System.out.println("[Skeleton]: the address will be chosen by the system when start method is called.");
		this.address = address;
	}

	/**
	 * Called when the listening thread exits.
	 * 
	 * <p>
	 * The listening thread may exit due to a top-level exception, or due to a call
	 * to <code>stop</code>.
	 * 
	 * <p>
	 * When this method is called, the calling thread owns the lock on the
	 * <code>Skeleton</code> object. Care must be taken to avoid deadlocks when
	 * calling <code>start</code> or <code>stop</code> from different threads during
	 * this call.
	 * 
	 * <p>
	 * The default implementation does nothing.
	 * 
	 * @param cause The exception that stopped the skeleton, or <code>null</code> if
	 *              the skeleton stopped normally.
	 */
	protected void stopped(Throwable cause) {
	}

	/**
	 * Called when an exception occurs at the top level in the listening thread.
	 * 
	 * <p>
	 * The intent of this method is to allow the user to report exceptions in the
	 * listening thread to another thread, by a mechanism of the user's choosing.
	 * The user may also ignore the exceptions. The default implementation simply
	 * stops the server. The user should not use this method to stop the skeleton.
	 * The exception will again be provided as the argument to <code>stopped</code>,
	 * which will be called later.
	 * 
	 * @param exception The exception that occurred.
	 * @return <code>true</code> if the server is to resume accepting connections,
	 *         <code>false</code> if the server is to shut down.
	 */
	protected boolean listen_error(Exception exception) {
		return false;
	}

	/**
	 * Called when an exception occurs at the top level in a service thread.
	 * 
	 * <p>
	 * The default implementation does nothing.
	 * 
	 * @param exception The exception that occurred.
	 */
	protected void service_error(RMIException exception) {
	}

	/**
	 * Starts the skeleton server.
	 * 
	 * <p>
	 * A thread is created to listen for connection requests, and the method returns
	 * immediately. Additional threads are created when connections are accepted.
	 * The network address used for the server is determined by which constructor
	 * was used to create the <code>Skeleton</code> object.
	 * 
	 * @throws RMIException When the listening socket cannot be created or bound,
	 *                      when the listening thread cannot be created, or when the
	 *                      server has already been started and has not since
	 *                      stopped.
	 */
	public synchronized void start() throws RMIException {
		if (this.started) {
			throw new RMIException("Server has already been started");
		}
		if (this.isStopped) {
			throw new RMIException("Server stopped");
		}

		// bind server socket to specific address.
		try {
			(serverSocket = new ServerSocket()).bind(this.address);
			this.address = (InetSocketAddress) this.serverSocket.getLocalSocketAddress();
		} catch (IOException e) {
			throw new RMIException(e);
		}

		// thread is created to listen for connection requests:- Listener Thread
		try {
			new Thread(new ListenerThread()).start();
		} catch (RuntimeException e) {
			Util.closeServerSocketConnection(this.serverSocket);
			throw e;
		}
		this.started = true;
	}

	private class ListenerThread implements Runnable {
		@Override
		public void run() {
			Throwable t = null;
			try {
				while (!Skeleton.this.isStopped) {
					Socket socketAccept;
					try {
						// method is blocked until connection is made.
						// accept a connection on this socket.
						socketAccept = Skeleton.this.serverSocket.accept();
					} catch (Exception e) {
						// allow user to report exceptions in the listening thread to another thread
						if (!Skeleton.this.isStopped && Skeleton.this.listen_error(e)) {
							continue;
						}
						throw e;
					}
					// Additional threads are created when connections are accepted.:- Service
					// Threads
					new Thread(new ServiceThread(socketAccept)).start();
				}
			} catch (Exception e2) {
				t = e2;
			}

			Util.closeServerSocketConnection(Skeleton.this.serverSocket);

			// called when listening thread exits
			Skeleton.this.stopped(t);
		}
	}

	private class ServiceThread implements Runnable {

		private Socket socket;

		public ServiceThread(Socket socket) {
			this.socket = socket;
		}

		private void sendResponseToClient(String message, final Throwable t, ObjectOutputStream objectoutputStream)
				throws RMIException {
			try {
				// throw RMIException to stub client side with customized message
				RMIException object = new RMIException(
						"[Skeleton]: Remote exception from Skeleton Server Side: " + message, t);
				// pass false boolean flag
				objectoutputStream.writeBoolean(false);
				// pass error message to stub client side
				objectoutputStream.writeObject(object);
			} catch (Exception exception) {
			}
			throw new RMIException(message, t);
		}

		@Override
		public void run() {
			ObjectOutputStream objectOutputStream = null;
			ObjectInputStream objectInputStream = null;
			try {
				// create output stream and flush it
				try {
					objectOutputStream = new ObjectOutputStream(this.socket.getOutputStream());
					objectOutputStream.flush();
				} catch (Throwable t1) {
					throw new RMIException("[Sekelton]: Failed to create object output stream", t1);
				}

				// create input stream
				try {
					objectInputStream = new ObjectInputStream(this.socket.getInputStream());
				} catch (Throwable t2) {
					throw new RMIException("[Sekelton]: Failed to create object Input stream", t2);
				}
				String methodName = null;
				Class[] parameterTypes = null;
				Object[] args = null;
				Object object = null;
				boolean booleanValue = true;
				Method remoteMethod = null;

				// read method name from stub client side
				try {
					methodName = (String) objectInputStream.readObject();
				} catch (Throwable t3) {
					sendResponseToClient("[Sekelton]: Failed to read method name from Object Input Stream", t3,
							objectOutputStream);
				}

				// read parameter types :- array of parameters
				try {
					parameterTypes = (Class[]) objectInputStream.readObject();
				} catch (Throwable t4) {
					sendResponseToClient("[Sekelton]: Failed to read method parameter types from Object Input Stream",
							t4, objectOutputStream);
				}

				// array of object arguments
				try {
					args = (Object[]) objectInputStream.readObject();
				} catch (Throwable t5) {
					sendResponseToClient("[Sekelton]: Failed to read method arguments from Object Input Stream", t5,
							objectOutputStream);
				}

				// get method of same method name from remote interface
				try {
					remoteMethod = Skeleton.this.c.getMethod(methodName, parameterTypes);
				} catch (Throwable t6) {
					sendResponseToClient("[Sekelton]: Failed to get method from Remote Interface: " + c.getName(), t6,
							objectOutputStream);
				}

				// invoke remote method
				// Invokes the underlying method represented by this method
				// object, on the specified object with the specified parameters.
				try {
					object = remoteMethod.invoke(Skeleton.this.server, args);
				}
				// it wraps an exception thrown by invoke method
				catch (InvocationTargetException e) {
					// pass cause of failure via object output stream to client stub
					object = e.getCause();
					// set boolean value false
					booleanValue = false;
				} catch (Throwable t7) {
					sendResponseToClient("[Skeleton]: Failed to invoke remote method: " + methodName, t7,
							objectOutputStream);
				}

				// write boolean value and object to client
				try {
					objectOutputStream.writeBoolean(booleanValue);
					objectOutputStream.writeObject(object);
				} catch (IOException e) {
					throw new RMIException("[Skeleton]: Failed to write result to object output stream (Client Stub)",
							e);
				}

			} catch (RMIException ex) {
				// when an exception occurs at the top level in a service thread call
				// service_error(Exception)
				Skeleton.this.service_error(ex);

			} finally {
				// flush and close output stream
				if (!CommonUtil.isNull(objectOutputStream)) {
					try {
						objectOutputStream.flush();
						objectOutputStream.close();
					} catch (Exception ex10) {
					}
				}
				// flush and close input stream
				if (!CommonUtil.isNull(objectInputStream)) {
					try {
						objectInputStream.close();
					} catch (Exception ex12) {
					}
				}
				Util.closeSocketConnection(socket);
			}
		}
	}

	/**
	 * Stops the skeleton server, if it is already running.
	 * 
	 * <p>
	 * The listening thread terminates. Threads created to service connections may
	 * continue running until their invocations of the <code>service</code> method
	 * return. The server stops at some later time; the method <code>stopped</code>
	 * is called at that point. The server may then be restarted.
	 */
	public synchronized void stop() {
		this.started = false;
		this.isStopped = true;
		Util.closeServerSocketConnection(this.serverSocket);
	}

	InetSocketAddress getAddress() throws UnknownHostException {
		if (CommonUtil.isNull(this.address)) {
			throw new IllegalStateException("[Skeleton] skeleton has not been assigned address by the user");
		}

		// it checks if the skeleton address is a wildcard, if its wildcard it will
		// return the address of local host and get port number.
		if (this.address.getAddress().isAnyLocalAddress()) {
			return new InetSocketAddress(InetAddress.getLocalHost(), this.address.getPort());
		}
		return this.address;
	}

	int getPort() {
		if (CommonUtil.isNull(this.address)) {
			throw new IllegalStateException("[Skeleton] skeleton has not been assigned address by the user");
		}
		if (this.address.getPort() == 0) {
			throw new IllegalStateException("[Skeleton] skeleton has not been assigned port");
		}
		// return port of this skeleton
		return this.address.getPort();
	}
}
