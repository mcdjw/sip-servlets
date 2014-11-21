/*
 *
 * WSC                             Controller               3pcc
 *  | MESSAGE "call"                   |                      |
 *  |--------------------------------->|                      |
 *  | 200 OK                           |                      |
 *  |<---------------------------------|                      |
 *  | MESSAGE "call_created"           |                      |
 *  |<---------------------------------|                      |
 *  | 200 OK                           |                      |
 *  |--------------------------------->|                      |
 *  |                                  | INVITE               |
 *  |                                  |--------------------->|
 *  |                                  | 180 Ringing          |
 *  |                                  |<---------------------|
 *  |                                  | 183 Session Progress |
 *  |                                  |<---------------------|
 *  | MESSAGE "source_connected"       |                      |
 *  |<---------------------------------|                      |
 *  | 200 OK                           |                      |
 *  |--------------------------------->|                      |
 *  |                                  | 200 OK               |
 *  |                                  |<---------------------|
 *  | MESSAGE "destination_connected"  |                      |
 *  |<---------------------------------|                      |
 *  | 200 OK                           |                      |
 *  |--------------------------------->|                      |
 *  |                                  | ACK                  |
 *  |                                  |--------------------->|
 *  | MESSAGE "call_connected"         |                      |
 *  |<---------------------------------|                      |
 *  | 200 OK                           |                      |
 *  |--------------------------------->|                      |
 *  |                                  | BYE                  |
 *  |                                  |<---------------------|
 *  | MESSAGE "call_completed"         |                      |
 *  |<---------------------------------|                      |
 *  | 200 OK                           |                      |
 *  |--------------------------------->|                      |
 *  |                                  |                      |
 *
 */

package oracle.communications.talkbac;

import java.io.IOException;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Resource;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.servlet.ServletException;
import javax.servlet.sip.Address;
import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipApplicationSessionEvent;
import javax.servlet.sip.SipApplicationSessionListener;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletContextEvent;
import javax.servlet.sip.SipServletListener;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSessionsUtil;
import javax.servlet.sip.SipURI;
import javax.servlet.sip.TimerListener;
import javax.servlet.sip.TimerService;
import javax.servlet.sip.annotation.SipApplicationKey;
import javax.servlet.sip.annotation.SipListener;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;

import weblogic.kernel.KernelLogManager;

@SipListener
public class TalkBACSipServlet extends SipServlet implements SipServletListener, TimerListener, SipApplicationSessionListener {
	static Logger logger;
	{
		logger = Logger.getLogger(TalkBACSipServlet.class.getName());
		logger.setParent(KernelLogManager.getLogger());
	}

	public final static org.apache.logging.log4j.Logger cdr = org.apache.logging.log4j.LogManager.getLogger(TalkBACSipServlet.class.getName());

	private enum SipMethod {
		INVITE, ACK, BYE, CANCEL, OPTIONS, REGISTER, PRACK, SUBSCRIBE, NOTIFY, PUBLISH, INFO, REFER, MESSAGE, UPDATE
	}

	private enum CallControl {
		call, terminate, transfer, hold, mute, resume, dial, redirect, accept, reject, conference, release
	}

	public final static String REQUEST_ID = "request_id";
	public final static String CALL_CONTROL = "call_control";
	public final static String CLIENT_ADDRESS = "CLIENT_ADDRESS";
	public final static String ORIGIN_ADDRESS = "ORIGIN_ADDRESS";
	public final static String DESTINATION_ADDRESS = "DESTINATION_ADDRESS";
	public final static String ORIGIN_SESSION_ID = "ORIGIN_SESSION_ID";
	public final static String DESTINATION_SESSION_ID = "DESTINATION_SESSION_ID";
	public final static String GATEWAY = "GATEWAY";
	public final static String USER = "USER";
	public final static String MESSAGE_UTILITY = "MESSAGE_UTILITY";

	public static String callInfo = null;

	public static String listenAddress = null;
	public static String servletName = null;

	public static Address talkBACAddress = null;

	private int defaultCallflow = 6;

	public static boolean disableAuth = false;

	public static String ldapProviderURL = null;
	public static String ldapUser = null;
	public static String ldapPassword = null;
	public static String ldapUserDN = null;
	public static String ldapFilter = null;
	public static String ldapLocationParameter = null;

	public static Hashtable ldapEnv;

	public static long keepAlive;
	public static String appName = null;

	@Resource
	public static SipFactory factory;

	@Resource
	public static SipSessionsUtil util;

	@Resource
	public static TimerService timer;

	@SipApplicationKey
	public static String sessionKey(SipServletRequest request) {
		String key = null;

		switch (SipMethod.valueOf(request.getMethod())) {
		case MESSAGE:
		case REGISTER:
			key = ((SipURI) request.getFrom().getURI()).getUser().toLowerCase();
		default:
		}

		return key;
	}

	public static String generateKey(Address address) {
		SipURI uri = (SipURI) address.getURI();
		String key = uri.getUser().toLowerCase() + "@" + uri.getHost().toLowerCase();
		return key;
	}

	public String getParameter(SipServletContextEvent event, String name) {
		String value = System.getProperty(name);
		value = (value != null) ? value : event.getServletContext().getInitParameter(name);
		return value;
	}

	@Override
	public void servletInitialized(SipServletContextEvent event) {
		logger.info(event.getSipServlet().getServletName() + " initialized.");

		try {
			servletName = event.getSipServlet().getServletName();

			listenAddress = System.getProperty("listenAddress");
			listenAddress = (listenAddress != null) ? listenAddress : event.getServletContext().getInitParameter("listenAddress");

			logger.info("listenAddress: " + listenAddress);

			callInfo = "<sip:" + listenAddress + ">;method=\"NOTIFY;Event=telephone-event;Duration=500\"";

			String strKeepAlive = System.getProperty("keepAlive");
			strKeepAlive = (strKeepAlive != null) ? strKeepAlive : event.getServletContext().getInitParameter("keepAlive");
			keepAlive = Long.parseLong(strKeepAlive) * 1000;

			String strDefaultCallflow = System.getProperty("defaultCallflow");
			strDefaultCallflow = (strDefaultCallflow != null) ? strDefaultCallflow : event.getServletContext().getInitParameter("defaultCallflow");
			if (strDefaultCallflow != null) {
				defaultCallflow = Integer.parseInt(strDefaultCallflow);
			}
			logger.info("defaultCallflow: " + defaultCallflow);

			appName = System.getProperty("appName");
			appName = (appName != null) ? appName : event.getServletContext().getInitParameter("appName");
			appName = (appName != null) ? appName : servletName;

			talkBACAddress = factory.createAddress("<sip:" + appName + "@" + listenAddress + ">");

			String strDisableAuth = System.getProperty("disableAuth");
			strDisableAuth = (strDisableAuth != null) ? strDisableAuth : event.getServletContext().getInitParameter("disableAuth");
			if (strDisableAuth != null) {
				disableAuth = Boolean.parseBoolean(strDisableAuth);
			}
			logger.info("disableAuth: " + disableAuth);

			// LDAP
			ldapProviderURL = getParameter(event, "ldapProviderURL");
			logger.info("ldapProviderURL: " + ldapProviderURL);
			ldapUser = getParameter(event, "ldapUser");
			logger.info("ldapUser: " + ldapUser);
			ldapPassword = getParameter(event, "ldapPassword");
			logger.info("ldapPassword: " + ldapPassword);
			ldapUserDN = getParameter(event, "ldapUserDN");
			logger.info("ldapUserDN: " + ldapUserDN);
			ldapFilter = getParameter(event, "ldapFilter");
			logger.info("ldapFilter: " + ldapFilter);
			ldapLocationParameter = getParameter(event, "ldapLocationParameter");
			logger.info("ldapLocationParameter: " + ldapLocationParameter);

			ldapEnv = new Hashtable();
			ldapEnv.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
			ldapEnv.put(Context.PROVIDER_URL, ldapProviderURL);
			ldapEnv.put(Context.SECURITY_AUTHENTICATION, "simple");
			ldapEnv.put(Context.SECURITY_PRINCIPAL, ldapUser);
			ldapEnv.put(Context.SECURITY_CREDENTIALS, ldapPassword);
			ldapEnv.put("com.sun.jndi.ldap.connect.pool", "true");

			// ldapCtx = new InitialDirContext(ldapEnv);

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public static DirContext connectLdap() throws NamingException {
		return new InitialDirContext(ldapEnv);
	}

	public static void disconnectLdap(DirContext ldapCtx, NamingEnumeration results) throws NamingException {
		results.close();
		ldapCtx.close();
	}

	public static NamingEnumeration ldapSearch(DirContext ldapCtx, String userId, String objectSid) throws NamingException {
		NamingEnumeration results = null;

		try {

			String filter = new String(ldapFilter);
			filter = filter.replace("${userId}", userId);
			filter = filter.replace("${objectSID}", objectSid);

			logger.fine("filter: " + filter);

			SearchControls controls = new SearchControls();
			controls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
			results = ldapCtx.search("", filter, controls);

			logger.fine("results = " + results);

		} catch (Exception e) {
			e.printStackTrace();
		}

		return results;
	}

	@Override
	protected void doRequest(SipServletRequest request) throws ServletException, IOException {
		boolean printed = false;
		SipServletResponse response;
		CallStateHandler handler = null;
		TalkBACMessage msg = null;
		int state = 0; // just for printing info
		TalkBACMessageUtility msgUtility = null;
		SipApplicationSession appSession;

		appSession = request.getApplicationSession();
		msgUtility = (TalkBACMessageUtility) appSession.getAttribute(MESSAGE_UTILITY);

		try {

			if (request.getMethod().equals("BYE")) {

				String ignore_bye = (String) appSession.getAttribute("IGNORE_BYE");
				if (ignore_bye != null && ignore_bye.equals(request.getCallId())) {
					// do nothing;
					handler = new GenericResponse();
				} else {
					handler = new TerminateCall();
					handler.printInboundMessage(request);
					printed = true;

					response = request.createResponse(200);
					response.send();
					handler.printOutboundMessage(response);

					// msg = new TalkBACMessage(request.getApplicationSession(),
					// "call_completed");
					// msg.send();
				}

			} else if (request.getMethod().equals("REGISTER")) {
				handler = new Authentication();
				handler.printInboundMessage(request);
				printed = true;
			}

			if (handler == null) {
				handler = (CallStateHandler) request.getSession().getAttribute(CallStateHandler.CALL_STATE_HANDLER);
				msgUtility = (TalkBACMessageUtility) request.getApplicationSession().getAttribute(MESSAGE_UTILITY);
			}

			if (handler == null) {
				handler = (CallStateHandler) request.getApplicationSession().getAttribute(CallStateHandler.CALL_STATE_HANDLER);
				msgUtility = (TalkBACMessageUtility) request.getApplicationSession().getAttribute(MESSAGE_UTILITY);
			}

			if (handler == null) {
				switch (SipMethod.valueOf(request.getMethod())) {

				case MESSAGE:
					// JWM

					cdr.info(request.getContent().toString().replaceAll("[\n\r]", ""));
					response = request.createResponse(200);
					response.send();

					ObjectMapper objectMapper = new ObjectMapper();
					JsonNode rootNode = objectMapper.readTree(request.getContent().toString());
					String cc = rootNode.path(CALL_CONTROL).asText();
					String requestId = rootNode.path(REQUEST_ID).asText();

					if (requestId != null && requestId.length()>0 && requestId.equals("null")==false) {
						appSession = util.getApplicationSessionById(requestId);
					} else {
						appSession = factory.createApplicationSession();
					}

					appSession.setAttribute(USER, request.getSession().getRemoteParty().getURI().toString());
					msgUtility = (TalkBACMessageUtility) appSession.getAttribute(MESSAGE_UTILITY);
					msgUtility = (msgUtility != null) ? msgUtility : new TalkBACMessageUtility(request.getApplicationSession());

					if (cc != null) {
						switch (CallControl.valueOf(cc)) {
						case call: {
							Address originAddress = factory.createAddress(rootNode.path("origin").asText());
							Address destinationAddress = factory.createAddress(rootNode.path("destination").asText());

							// handler = new MakeCall(originAddress,
							// destinationAddress);
							// msgUtility.addClient(request.getFrom());
							// msgUtility.addEndpoint(originAddress);
							// msgUtility.addEndpoint(destinationAddress);

							handler = new CallFlow5(originAddress, destinationAddress);
							msgUtility.addClient(request.getFrom());
							msgUtility.addEndpoint(originAddress);
							msgUtility.addEndpoint(destinationAddress);

							break;
						}
						case terminate: {
							handler = new TerminateCall();
							break;
						}
						case dial: {
							String digits = rootNode.path("digits").asText();
							Address destinationAddress = factory.createAddress(rootNode.path("destination").asText());
							handler = new DtmfRelay(destinationAddress, digits);
							break;
						}
						case hold: {
							Address destinationAddress = factory.createAddress(rootNode.path("destination").asText());
							handler = new Hold(destinationAddress);
							break;
						}
						case resume: {
							Address originAddress = factory.createAddress(rootNode.path("origin").asText());
							Address destinationAddress = factory.createAddress(rootNode.path("destination").asText());
							handler = new Resume(originAddress, destinationAddress);
							break;
						}
						case mute: {
							Address originAddress = factory.createAddress(rootNode.path("origin").asText());
							Address destinationAddress = factory.createAddress(rootNode.path("destination").asText());
							handler = new Mute(originAddress, destinationAddress);
							break;
						}
						case transfer: {
							Address originAddress = factory.createAddress(rootNode.path("origin").asText());
							Address destinationAddress = factory.createAddress(rootNode.path("destination").asText());
							Address targetAddress = factory.createAddress(rootNode.path("target").asText());
							handler = new Transfer(originAddress, destinationAddress, targetAddress);
							msgUtility.addEndpoint(targetAddress);
							break;
						}
						case conference: {
							Address originAddress = factory.createAddress(rootNode.path("origin").asText());
							Address targetAddress = factory.createAddress(rootNode.path("target").asText());
							handler = new Conference(originAddress, targetAddress);
							msgUtility.addEndpoint(targetAddress);
							break;
						}
						case release:
						case redirect:
						case accept:
						case reject:
						default: {
							handler = new NotImplemented();
							break;
						}
						}
						break;
					}

				case REGISTER: {
					handler = new Authentication();
					handler.printInboundMessage(request);
					printed = true;
					break;
				}
				case INVITE: {
					if (request.isInitial()) {
						handler = new AcceptCall();
						if (msgUtility == null) {
							msgUtility = new TalkBACMessageUtility(appSession);
						}
						msgUtility.addEndpoint(request.getFrom());
						msgUtility.addEndpoint(request.getTo());
						handler.printInboundMessage(request);
					} else {
						handler = new Reinvite();
						handler.printInboundMessage(request);
					}

					printed = true;
					break;
				}
				case CANCEL:
				case BYE: {
					handler = new TerminateCall();
					msgUtility.addEndpoint(request.getFrom());
					msgUtility.addEndpoint(request.getTo());

					handler.printInboundMessage(request);
					printed = true;

					response = request.createResponse(200);
					response.send();
					handler.printOutboundMessage(response);

					break;
				}

				case INFO:
				case PRACK:
				case SUBSCRIBE:
				case PUBLISH:
				case REFER:
					handler = new NotImplemented();
					handler.printInboundMessage(request);
					printed = true;
					break;

				case OPTIONS:
					handler = new Options();
					handler.printInboundMessage(request);
					printed = true;
					break;

				case NOTIFY:
					String event;
					event = request.getHeader("Event");
					if (event != null && event.equalsIgnoreCase("kpml")) {
						handler = new KpmlRelay(request.getFrom(), request.getTo());
						handler.printInboundMessage(request);
						printed = true;
						break;
					}
					// passthru

				case UPDATE:
				case ACK:
				default:
					handler = new GenericResponse();
					handler.printInboundMessage(request);
					printed = true;
					break;
				}
			}

			if (printed == false) {
				handler.printInboundMessage(request);
			}
			handler.processEvent(appSession, msgUtility, request, null, null);
			if (msgUtility != null) {
				appSession.setAttribute(MESSAGE_UTILITY, msgUtility);
			}

		} catch (Exception e) {
			e.printStackTrace();

			try {
				handler = new TerminateCall();
				if (printed == false) {
					handler.printInboundMessage(request);
				}
				handler.processEvent(appSession, msgUtility, request, null, null);
				if (msgUtility != null) {
					appSession.setAttribute(MESSAGE_UTILITY, msgUtility);
				}

			} catch (Exception e2) {
				e2.printStackTrace();
			}

		}

	}

	@Override
	protected void doResponse(SipServletResponse response) throws ServletException, IOException {
		CallStateHandler handler = (CallStateHandler) response.getSession().getAttribute(CallStateHandler.CALL_STATE_HANDLER);
		TalkBACMessageUtility msgUtility = (TalkBACMessageUtility) response.getApplicationSession().getAttribute(MESSAGE_UTILITY);

		SipApplicationSession appSession = response.getApplicationSession();

		try {
			if (handler != null) {
				handler.printInboundMessage(response);
				handler.processEvent(appSession, msgUtility, null, response, null);
				if (msgUtility != null) {
					appSession.setAttribute(MESSAGE_UTILITY, msgUtility);
				}

			} else {
				// logger.fine("--> " + this.getClass().getSimpleName() +
				// " " + response.getMethod()
				// + response.getReasonPhrase() + " " + response.getTo());
			}
		} catch (Exception e) {
			e.printStackTrace();
			handler = new TerminateCall();
			try {
				handler.printInboundMessage(response);
				handler.processEvent(appSession, msgUtility, null, response, null);
				if (msgUtility != null) {
					appSession.setAttribute(MESSAGE_UTILITY, msgUtility);
				}

			} catch (Exception e1) {
				// do nothing;
			}
		}

	}

	@Override
	public void timeout(ServletTimer timer) {
		CallStateHandler handler;

		SipApplicationSession appSession = timer.getApplicationSession();
		TalkBACMessageUtility msgUtility = (TalkBACMessageUtility) appSession.getAttribute(MESSAGE_UTILITY);

		try {
			logger.fine("timeout... ");
			handler = (CallStateHandler) timer.getInfo();
			handler.processEvent(appSession, msgUtility, null, null, timer);
			if (msgUtility != null) {
				appSession.setAttribute(MESSAGE_UTILITY, msgUtility);
			}

		} catch (Exception e) {
			e.printStackTrace();
			handler = new TerminateCall();
			try {
				handler.processEvent(appSession, msgUtility, null, null, timer);
				if (msgUtility != null) {
					appSession.setAttribute(MESSAGE_UTILITY, msgUtility);
				}
			} catch (Exception e1) {
				// do nothing;
			}
		}
	}

	@Override
	public void sessionCreated(SipApplicationSessionEvent event) {
		if (logger.isLoggable(Level.FINE)) {
			System.out.println("ApplicationSession [" + event.getApplicationSession().toString().hashCode() + "] created.");
		}

	}

	@Override
	public void sessionDestroyed(SipApplicationSessionEvent event) {
		if (logger.isLoggable(Level.FINE)) {
			System.out.println("ApplicationSession [" + event.getApplicationSession().toString().hashCode() + "] destroyed.");
		}
	}

	@Override
	public void sessionExpired(SipApplicationSessionEvent event) {
		if (logger.isLoggable(Level.FINE)) {
			System.out.println("ApplicationSession [" + event.getApplicationSession().toString().hashCode() + "] expired.");
		}
	}

	@Override
	public void sessionReadyToInvalidate(SipApplicationSessionEvent event) {
		if (logger.isLoggable(Level.FINE)) {
			System.out.println("ApplicationSession [" + event.getApplicationSession().toString().hashCode() + "] ready to invalidate.");
		}
	}

}
