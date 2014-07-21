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
	public static Address outboundProxy=null;

	
	final static String INITIATOR = "INITIATOR";
	final static String ORIGIN_SESSION_ID = "ORIGIN_SESSION_ID";
	final static String DESTINATION_SESSION_ID = "DESTINATION_SESSION_ID";
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
		String proxy = event.getServletContext().getInitParameter("OUTBOUND_PROXY");
		
		logger.info("Setting Outbound Proxy: "+proxy);

		if(proxy!=null){
			try {
				this.outboundProxy = factory.createAddress(proxy);
				((SipURI)this.outboundProxy.getURI()).setLrParam(true);				
			} catch (ServletParseException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	protected void doRequest(SipServletRequest request) throws ServletException, IOException {
		try {

			CallStateHandler handler = (CallStateHandler) request.getSession().getAttribute(CallStateHandler.CALL_STATE_HANDLER);

			if (handler == null) {

				if (request.getMethod().equals("INVITE")) {
					if (request.isInitial()) {
						handler = new CallFlow1();
					} else {
						handler = new Reinvite();
					}
				} else if (request.getMethod().equals("BYE")) {
					handler = new TerminateCall();
				}

			}

			if (handler == null) {
				handler = new NotImplemented();
			}

			System.out.println("ThirdPartyCallControlServlet " + request.getMethod() + " " + handler.getClass().getSimpleName() + " " + handler.state);
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
				logger.fine(handler.getClass() + " " + response.getMethod() + " " + response.getReasonPhrase() + " State: " + handler.state);
				handler.processEvent(null, response);
			}
		} catch (Exception e) {
			throw new ServletException(e);
		}

	}

}
