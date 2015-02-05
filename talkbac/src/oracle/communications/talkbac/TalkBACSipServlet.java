/*
 * This SIP Servlet is the main event listener.
 * The incoming message can either be a response or a new request.
 * If it is a new request, this Servlet will decide which CallStateHandler to use.
 * If it is a response (or non-initial request), it will use the CallStateHandler
 * serialized in the SipSession object.
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
import javax.servlet.sip.SipServletMessage;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;
import javax.servlet.sip.SipSessionsUtil;
import javax.servlet.sip.SipURI;
import javax.servlet.sip.TimerListener;
import javax.servlet.sip.TimerService;
import javax.servlet.sip.annotation.SipApplicationKey;
import javax.servlet.sip.annotation.SipListener;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;

import weblogic.kernel.KernelLogManager;

/**
 * @author jeff
 * 
 */
@SipListener
public class TalkBACSipServlet extends SipServlet implements SipServletListener, TimerListener, SipApplicationSessionListener {
	private static final long serialVersionUID = 1L;
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
		call, terminate, release, disconnect, transfer, hold, mute, unmute, resume, dial, redirect, accept, reject, conference, dtmf_subscribe, dtmf_unsubscribe
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

	private int defaultCallflow = 5;

	public static boolean disableAuth = false;

	public static String ldapProviderURL = null;
	public static String ldapUser = null;
	public static String ldapPassword = null;
	public static String ldapUserDN = null;
	public static String ldapFilter = null;
	public static String ldapLocationParameter = null;

	@SuppressWarnings("rawtypes")
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
		case INVITE:
			// // This is to cover the complexity of the REFER (ringback-tone) call flow.
			// // When the INVITE caused by the REFER comes in, it will not have the same Call-ID
			//
			// String from_user = ((SipURI) request.getFrom().getURI()).getUser().toLowerCase();
			String to_user = ((SipURI) request.getTo().getURI()).getUser().toLowerCase();

			if (null != util.getApplicationSessionByKey(to_user, false)) {
				key = to_user;
			}

			// key = from_user + ":" + to_user;

			break;

		case MESSAGE:
		case REGISTER:
			key = ((SipURI) request.getFrom().getURI()).getUser().toLowerCase();
		default:
		}

		return key;
	}

	public String getParameter(SipServletContextEvent event, String name) {
		String value = System.getProperty(name);
		value = (value != null) ? value : event.getServletContext().getInitParameter(name);
		return value;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
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

	@SuppressWarnings("rawtypes")
	public static void disconnectLdap(DirContext ldapCtx, NamingEnumeration results) throws NamingException {
		results.close();
		ldapCtx.close();
	}

	@SuppressWarnings("rawtypes")
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
		MessageUtility msgUtility = null;
		SipApplicationSession appSession;

		appSession = request.getApplicationSession();
		msgUtility = (MessageUtility) appSession.getAttribute(MESSAGE_UTILITY);

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
				}

			} else if (request.getMethod().equals("REGISTER")) {
				handler = new Authentication();
				handler.printInboundMessage(request);
				printed = true;
			}

			if (handler == null) {
				handler = (CallStateHandler) request.getSession().getAttribute(CallStateHandler.CALL_STATE_HANDLER);
				msgUtility = (MessageUtility) request.getApplicationSession().getAttribute(MESSAGE_UTILITY);
			}

			if (handler == null) {
				handler = (CallStateHandler) request.getApplicationSession().getAttribute(CallStateHandler.CALL_STATE_HANDLER);
				msgUtility = (MessageUtility) request.getApplicationSession().getAttribute(MESSAGE_UTILITY);
			}

			if (handler == null) {
				switch (SipMethod.valueOf(request.getMethod())) {

				case MESSAGE:
					response = request.createResponse(200);
					response.send();

					ObjectMapper objectMapper = new ObjectMapper();
					JsonNode rootNode = objectMapper.readTree(request.getContent().toString());

					String cc = rootNode.path(CALL_CONTROL).asText();
					String requestId = rootNode.path(REQUEST_ID).asText();

					if (requestId != null && requestId.length() > 0 && requestId.equals("null") == false) {
						appSession = util.getApplicationSessionById(requestId);
					} else {
						String key = Integer.toString(Math.abs(request.getCallId().hashCode()));
						appSession = util.getApplicationSessionByKey(key, true);
						appSession.setAttribute(CallStateHandler.KEY, key);
					}

					if (appSession == null) {
						msgUtility = new MessageUtility();
						msgUtility.addClient(request.getFrom());
						msg = new TalkBACMessage();
						msg.setParameter("event", "error");
						msg.setParameter("request_id", requestId);
						msg.setStatus(500, "Invalid request_id");
						SipServletRequest outMsg = msgUtility.send(msg);

						if (logger.isLoggable(Level.FINE)) {
							CallStateHandler h = new InvalidateSession();
							h.printOutboundMessage(outMsg);
						}

						return;
					}

					appSession.setAttribute(USER, request.getSession().getRemoteParty().getURI().toString());
					msgUtility = (MessageUtility) appSession.getAttribute(MESSAGE_UTILITY);
					msgUtility = (msgUtility != null) ? msgUtility : new MessageUtility();

					String gateway = (String) request.getApplicationSession().getAttribute(GATEWAY);

					Address originAddress = null;
					Address destinationAddress = null;
					Address targetAddress = null;

					String origin = rootNode.path("origin").asText();
					if (origin != null && origin.length() > 0 && false == origin.equals("null")) {
						originAddress = factory.createAddress(origin);
						if (gateway != null) {
							String originUser = ((SipURI) originAddress.getURI()).getUser().toLowerCase();
							originAddress = TalkBACSipServlet.factory.createAddress("<sip:" + originUser + "@" + gateway + ">");
						}
					}

					String destination = rootNode.path("destination").asText();
					if (destination != null && destination.length() > 0 && false == destination.equals("null")) {
						destinationAddress = factory.createAddress(destination);
						if (gateway != null) {
							String destinationUser = ((SipURI) destinationAddress.getURI()).getUser().toLowerCase();
							destinationAddress = TalkBACSipServlet.factory.createAddress("<sip:" + destinationUser + "@" + gateway + ">");
						}
					}

					String target = rootNode.path("target").asText();
					if (target != null && target.length() > 0 && false == target.equals("null")) {
						targetAddress = factory.createAddress(target);
						if (gateway != null) {
							String targetUser = ((SipURI) targetAddress.getURI()).getUser().toLowerCase();
							targetAddress = TalkBACSipServlet.factory.createAddress("<sip:" + targetUser + "@" + gateway + ">");
						}
					}

					if (cc != null) {
						switch (CallControl.valueOf(cc)) {
						case call: {
							int call_flow = defaultCallflow;
							String strCallFlow = rootNode.path("call_flow").asText();
							if (strCallFlow != null) {
								call_flow = Integer.parseInt(strCallFlow);
							}

							msgUtility.addClient(request.getFrom());
							msgUtility.addClient(originAddress);
							msgUtility.addClient(destinationAddress);

							switch (call_flow) {
							case 1:
								handler = new CallFlow1(originAddress, destinationAddress);
								break;
							case 2:
								handler = new CallFlow2(originAddress, destinationAddress);
								break;
							case 3:
								handler = new CallFlow3(originAddress, destinationAddress);
								break;
							case 4:
								handler = new CallFlow4(originAddress, destinationAddress);
								break;
							case 5:
								handler = new CallFlow5(originAddress, destinationAddress);
								break;
							case 6:
								handler = new CallFlow6(originAddress, destinationAddress);
								break;
							default:
								handler = new CallFlow5(originAddress, destinationAddress);

							}

							break;
						}

						case disconnect: {
							handler = new Disconnect(targetAddress);
						}
						case terminate: {
							handler = new TerminateCall();
							break;
						}
						case dial: {
							String digits = rootNode.path("digits").asText();
							handler = new DtmfRelay(destinationAddress, digits);
							break;
						}
						case dtmf_subscribe: {
							handler = new KpmlRelay(3600);
							break;
						}
						case dtmf_unsubscribe: {
							handler = new KpmlRelay(0);
							break;
						}

						case hold: {
							handler = new Hold(destinationAddress);
							break;
						}
						case unmute: {
							handler = new Unmute(originAddress, destinationAddress);
							break;
						}
						case resume: {
							handler = new Resume(originAddress, destinationAddress);
							break;
						}
						case mute: {
							handler = new Mute(originAddress, destinationAddress);
							break;
						}
						case transfer: {
							handler = new Transfer(originAddress, destinationAddress, targetAddress);
							msgUtility.addClient(targetAddress);
							break;
						}
						case conference: {
							handler = new Conference(originAddress, targetAddress);
							msgUtility.addClient(targetAddress);
							break;
						}

						case release: {
							handler = new Release(targetAddress);
							break;
						}

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
							msgUtility = new MessageUtility();
						}
						msgUtility.addClient(request.getFrom());
						msgUtility.addClient(request.getTo());
						handler.printInboundMessage(request);
					} else {
						handler = (CallStateHandler) appSession.getAttribute(CallStateHandler.CALL_STATE_HANDLER);
						if (handler == null) {
							handler = new Reinvite();
						}
						handler.printInboundMessage(request);
					}

					printed = true;
					break;
				}
				case CANCEL:
				case BYE: {
					handler = new TerminateCall();
					msgUtility.addClient(request.getFrom());
					msgUtility.addClient(request.getTo());

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
						// handler = new KpmlRelay(request.getFrom(),
						// request.getTo());
						handler = new KpmlRelay(3600);
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
			if (msgUtility != null && appSession.isValid()) {
				appSession.setAttribute(MESSAGE_UTILITY, msgUtility);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	@Override
	protected void doResponse(SipServletResponse response) throws ServletException, IOException {
		try {
			SipApplicationSession appSession = response.getApplicationSession();
			MessageUtility msgUtility = (MessageUtility) appSession.getAttribute(MESSAGE_UTILITY);
			msgUtility = (msgUtility != null) ? msgUtility : new MessageUtility();

			CallStateHandler handler = (CallStateHandler) response.getSession().getAttribute(CallStateHandler.CALL_STATE_HANDLER);
			handler = (CallStateHandler) ((handler != null) ? handler : appSession.getAttribute(CallStateHandler.CALL_STATE_HANDLER));
			handler = (handler != null) ? handler : new GenericResponse();

			handler.printInboundMessage(response);
			handler.processEvent(appSession, msgUtility, null, response, null);
			if (appSession.isValid() && msgUtility != null) {
				appSession.setAttribute(MESSAGE_UTILITY, msgUtility);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	@Override
	public void timeout(ServletTimer timer) {
		if (logger.isLoggable(Level.FINE)) {
			logger.fine("ApplicationSession [" + timer.getApplicationSession().getId().hashCode() + "] timer expired.");
			// System.out.println("ApplicationSession [" + timer.getApplicationSession().getId().hashCode() +
			// "] timer expired.");
		}

		CallStateHandler handler;

		SipApplicationSession appSession = timer.getApplicationSession();
		MessageUtility msgUtility = (MessageUtility) appSession.getAttribute(MESSAGE_UTILITY);

		try {
			handler = (CallStateHandler) timer.getInfo();
			handler.printTimer(timer);
			handler.processEvent(appSession, msgUtility, null, null, timer);
			if (msgUtility != null) {
				appSession.setAttribute(MESSAGE_UTILITY, msgUtility);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static String hexHash(SipApplicationSession appSession) {
		return "[" + Integer.toHexString(Math.abs(appSession.getId().hashCode())).toUpperCase() + "]";
	}

	public static String hexHash(SipSession sipSession) {
		return "[" + Integer.toHexString(Math.abs(sipSession.getId().hashCode())).toUpperCase() + "]";
	}

	public static String hexHash(SipServletMessage message) {
		String output = "[";
		output += Integer.toHexString(Math.abs(message.getApplicationSession().getId().hashCode())).toUpperCase();
		output += ":";
		output += Integer.toHexString(Math.abs(message.getSession().getId().hashCode())).toUpperCase();
		output += "]";
		return output;
	}

	@Override
	public void sessionCreated(SipApplicationSessionEvent event) {
		if (logger.isLoggable(Level.FINE)) {
			String output = hexHash(event.getApplicationSession()) + " created";
			logger.fine(output);
			System.out.println(output);
		}

	}

	@Override
	public void sessionDestroyed(SipApplicationSessionEvent event) {
		if (logger.isLoggable(Level.FINE)) {
			String output = hexHash(event.getApplicationSession()) + " destroyed";
			logger.fine(output);
			System.out.println(output);
		}
	}

	@Override
	public void sessionExpired(SipApplicationSessionEvent event) {
		if (logger.isLoggable(Level.FINE)) {
			String output = hexHash(event.getApplicationSession()) + " expired";
			logger.fine(output);
			System.out.println(output);
		}
	}

	@Override
	public void sessionReadyToInvalidate(SipApplicationSessionEvent event) {
		if (logger.isLoggable(Level.FINE)) {
			String output = hexHash(event.getApplicationSession()) + " ready to invalidate";
			logger.fine(output);
			System.out.println(output);
		}
	}

}
