package vorpal.sip.servlets.jsr289.callcontrol;

import java.io.IOException;
import java.util.Iterator;
import java.util.logging.Logger;

import javax.annotation.Resource;
import javax.servlet.ServletException;
import javax.servlet.sip.Address;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;
import javax.servlet.sip.SipURI;
import javax.servlet.sip.SipSession.State;
import javax.servlet.sip.annotation.SipApplicationKey;
import javax.servlet.sip.SipSessionsUtil;

import weblogic.kernel.KernelLogManager;

public class ThirdPartyCallControlServlet extends SipServlet {
	private final static String FROM_ADDRESS = "FROM_ADDRESS";
	private final static String TO_ADDRESS = "TO_ADDRESS";
	private final static String STATE = "STATE";
	private final static String FROM_RESPONSE = "FROM_RESPONSE";
	private final static String TO_SESSION_ID = "TO_SESSION_ID";
	private final static String FROM_SESSION_ID = "FROM_SESSION_ID";
	private final static String ORIGINAL_REQUEST = "ORIGINAL_REQUEST";
	private final static String SIP = SipApplicationSession.Protocol.SIP.toString();

	private enum ApplicationState {
		STATE01, STATE02, STATE03, STATE04, STATE05, STATE06
	}

	static Logger logger;
	{
		logger = Logger.getLogger(ThirdPartyCallControlServlet.class.getName());
		logger.setParent(KernelLogManager.getLogger());
	}

	@Resource
	public static SipFactory factory;

	@Resource
	public static SipSessionsUtil util;

	@Override
	protected void doInvite(SipServletRequest request) throws ServletException, IOException {
		if (request.getMethod().equals("INVITE")) {
			if (request.isInitial()) {

				SipApplicationSession appSession = request.getApplicationSession();
				SipServletRequest fromRequest = factory.createRequest(appSession, "INVITE", request.getTo(), request.getFrom());

				appSession.setAttribute(FROM_ADDRESS, request.getFrom());
				appSession.setAttribute(TO_ADDRESS, request.getTo());
				appSession.setAttribute(ORIGINAL_REQUEST, request);
				appSession.setAttribute(FROM_SESSION_ID, fromRequest.getSession().getId());

				fromRequest.getSession().setAttribute(STATE, ApplicationState.STATE01);

				fromRequest.send();
			} else {
				// Check for transfer
				//

			}
		}
	}

	@Override
	protected void doProvisionalResponse(SipServletResponse response) throws ServletException, IOException {
		doSuccessResponse(response);
	}

	@Override
	protected void doSuccessResponse(SipServletResponse response) throws ServletException, IOException {
		SipApplicationSession appSession = response.getApplicationSession();
		ApplicationState state = (ApplicationState) response.getSession().getAttribute(STATE);

		logger.fine("ThirdPartyCallControlServlet Response " + response.getMethod() + " " + response.getStatus() + " " + state.toString());

		switch (state) {
		case STATE01:
			appSession.setAttribute(FROM_RESPONSE, response);
			if (response.getStatus() == 200) {

				Address from = (Address) appSession.getAttribute(FROM_ADDRESS);
				Address to = (Address) appSession.getAttribute(TO_ADDRESS);

				SipServletRequest toRequest = factory.createRequest(appSession, "INVITE", from, to);

				appSession.setAttribute(TO_SESSION_ID, toRequest.getSession().getId());
				toRequest.getSession().setAttribute(STATE, ApplicationState.STATE02);

				toRequest.setContent(response.getContent(), response.getContentType());
				toRequest.send();
			} else {
				SipServletRequest originalRequest = (SipServletRequest) appSession.getAttribute(ORIGINAL_REQUEST);
				originalRequest.createResponse(response.getStatus()).send();
			}

			break;
		case STATE02:
			if (response.getStatus() == 200) {

				response.createAck().send();

				SipServletResponse fromResponse = (SipServletResponse) appSession.getAttribute(FROM_RESPONSE);
				SipServletRequest fromAck = fromResponse.createAck();
				fromAck.setContent(response.getContent(), response.getContentType());
				fromAck.send();

				SipServletRequest originalRequest = (SipServletRequest) appSession.getAttribute(ORIGINAL_REQUEST);
				originalRequest.createResponse(200).send();

				// //Async Notification of Call Completion
				// logger.fine("Sending INFO message");
				// String content = ""+
				// "{\"event\": \"call_connected\",\n"+
				// "\"request_id\": \"123XYZ\",\n"+
				// "\"status\": 200,\n"+
				// "\"reason\": OK}";
				// SipServletRequest info =
				// originalRequest.getSession().createRequest("INFO");
				// info.setContent(content, "text/plain");
				// info.send();

			} else {
				SipServletRequest originalRequest = (SipServletRequest) appSession.getAttribute(ORIGINAL_REQUEST);
				originalRequest.createResponse(response.getStatus(), response.getReasonPhrase()).send();

			}

			break;
		case STATE03:

			break;
		case STATE04:
			break;
		case STATE05:
			break;
		case STATE06:
			break;
		default:
			break;

		}

	}

	@SuppressWarnings("incomplete-switch")
	protected void disconnect(SipApplicationSession appSession, SipSession sipSession) throws ServletException, IOException {

		Iterator<?> sessions = appSession.getSessions(SIP);
		while (sessions.hasNext()) {
			SipSession ss = (SipSession) sessions.next();

			logger.fine("SipSession: " + ss.getId() + " " + ss.getState().toString());

			if (ss.getId() != sipSession.getId()) {
				switch (ss.getState()) {
				case CONFIRMED:
					ss.createRequest("BYE").send();
					ss.setAttribute(STATE, ApplicationState.STATE04);
					break;
				case INITIAL:
				case EARLY:
					ss.createRequest("CANCEL").send();
					ss.setAttribute(STATE, ApplicationState.STATE04);
					break;
				}
			}
		}

	}

	@Override
	protected void doBye(SipServletRequest request) throws ServletException, IOException {
		request.createResponse(200).send();
		disconnect(request.getApplicationSession(), request.getSession());
	}

	@Override
	protected void doCancel(SipServletRequest request) throws ServletException, IOException {
		request.createResponse(200).send();
		disconnect(request.getApplicationSession(), request.getSession());
	}

	@Override
	protected void doErrorResponse(SipServletResponse response) throws ServletException, IOException {
		logger.info("ERROR RESPONSE: " + response.getMethod() + " " + response.getStatus() + " " + response.getReasonPhrase());

		SipServletRequest originalRequest = (SipServletRequest) response.getApplicationSession().getAttribute(ORIGINAL_REQUEST);
		SipServletResponse originalResponse = originalRequest.createResponse(response.getStatus(), response.getReasonPhrase());
		originalResponse.send();

		disconnect(response.getApplicationSession(), response.getSession());
	}

	@Override
	protected void doInfo(SipServletRequest request) throws ServletException, IOException {
		request.createResponse(200).send();
		SipApplicationSession appSession = request.getApplicationSession();
		SipSession toSession = appSession.getSipSession((String) appSession.getAttribute(TO_SESSION_ID));
		SipServletRequest infoRequest = toSession.createRequest("INFO");
		infoRequest.setContent(request.getContent(), request.getContentType());
		infoRequest.send();
	}

}
