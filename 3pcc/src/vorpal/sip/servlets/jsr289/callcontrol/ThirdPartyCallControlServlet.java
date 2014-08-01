package vorpal.sip.servlets.jsr289.callcontrol;

import java.io.IOException;
import java.util.logging.Logger;

import javax.annotation.Resource;
import javax.servlet.ServletException;
import javax.servlet.sip.Address;
import javax.servlet.sip.ServletParseException;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletContextEvent;
import javax.servlet.sip.SipServletListener;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSessionsUtil;
import javax.servlet.sip.SipURI;
import javax.servlet.sip.annotation.SipListener;

import weblogic.kernel.KernelLogManager;

@SipListener
public class ThirdPartyCallControlServlet extends SipServlet implements SipServletListener {
	public static Address outboundProxy = null;
	private final static String DTMF_RELAY = "application/dtmf-relay";
	public static String strOutboundProxy=null;

	final static String INITIATOR = "INITIATOR";

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
	public void servletInitialized(SipServletContextEvent event) {
		strOutboundProxy = event.getServletContext().getInitParameter("OUTBOUND_PROXY");

		logger.info("Setting Outbound Proxy: " + strOutboundProxy);

		if (strOutboundProxy != null) {
			try {
				this.outboundProxy = factory.createAddress("sip:"+strOutboundProxy);
				((SipURI) this.outboundProxy.getURI()).setLrParam(true);
			} catch (ServletParseException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	protected void doRequest(SipServletRequest request) throws ServletException, IOException {
		try {
			CallStateHandler handler;

			handler = (CallStateHandler) request.getSession().getAttribute(CallStateHandler.CALL_STATE_HANDLER);

			// Don't care about state, just end the call
			if (request.getMethod().equals("BYE")) {
				handler = new TerminateCall();
			}

			if (handler == null) {

				if (request.getMethod().equals("INVITE")) {
					if (request.isInitial()) {
						int callflow = 1;
						String callflowHeader = request.getHeader("Callflow");
						if (callflowHeader != null) {
							callflow = Integer.parseInt(callflowHeader);
						}
						switch (callflow) {
						default:
						case 1:
							handler = new CallFlow1();
							break;
						case 2:
							handler = new CallFlow2();
							break;
						case 3:
							handler = new CallFlow3();
							break;
						case 4:
							handler = new CallFlow4();
							break;
						case 5:
							handler = new CallFlow5();
							break;
						}
					} else {
						handler = new Reinvite();
					}
				} else if (request.getMethod().equals("INFO")) {
					if (request.getContentType().equals(DTMF_RELAY)) {
						handler = new DtmfRelay();
					}

				}

			}

			if (handler == null) {
				handler = new NotImplemented();
			}

			System.out
					.println("ThirdPartyCallControlServlet  REQUEST: " + request.getMethod() + " " + handler.getClass().getSimpleName() + " " + handler.state);
			handler.processEvent(request, null);

		} catch (Exception e) {
			throw new ServletException(e);
		}

	}

	@Override
	protected void doResponse(SipServletResponse response) throws ServletException, IOException {
		SipServletRequest initiatorRequest = (SipServletRequest) response.getSession().getAttribute(INITIATOR);
		CallStateHandler handler = (CallStateHandler) response.getSession().getAttribute(CallStateHandler.CALL_STATE_HANDLER);

		System.out.println("ThirdPartyCallControlServlet RESPONSE: " + response.getMethod() + " " + response.getStatus() + " " + response.getReasonPhrase());

		try {
			if (handler != null) {
				System.out.println(handler.getClass() + " " + response.getMethod() + " "+response.getStatus()+" "+ response.getReasonPhrase() + " State: " + handler.state);
				logger.fine(handler.getClass() + " " + response.getMethod() + " " + +response.getStatus()+" "+response.getReasonPhrase() + " State: " + handler.state);
				handler.processEvent(null, response);
			}
		} catch (Exception e) {
			throw new ServletException(e);
		}

	}

}
