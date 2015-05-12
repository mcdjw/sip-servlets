package oracle.communications.sdp;

import java.io.IOException;
import java.util.Enumeration;
import java.util.ListIterator;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Resource;
import javax.servlet.ServletException;
import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSessionEvent;
import javax.servlet.sip.SipApplicationSessionListener;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletContextEvent;
import javax.servlet.sip.SipServletListener;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.TimerListener;
import javax.servlet.sip.annotation.SipListener;

import oracle.communications.sdp.CallStateHandler.SipMethod;
import weblogic.kernel.KernelLogManager;

@SipListener
public class SiprecServlet extends SipServlet implements SipServletListener, TimerListener, SipApplicationSessionListener {
	static Logger logger;
	{
		logger = Logger.getLogger(SiprecServlet.class.getName());
		logger.setParent(KernelLogManager.getLogger());
	}

	@Resource
	public static SipFactory factory;

	public static Vector<VrspPair> dialPlans = new Vector<VrspPair>();

	// @SipApplicationKey
	// public static String sessionKey(SipServletRequest request) {
	// String key = null;
	//
	// try {
	// Parameterable contentType =
	// request.getParameterableHeader("Content-Type");
	//
	// if (contentType != null &&
	// contentType.getValue().equals("multipart/mixed")) {
	// byte[] content = (byte[]) request.getContent();
	// String recording = new String(content);
	// String ucid = recording.substring(recording.indexOf("<apkt:ucid>") + 11,
	// recording.indexOf("</apkt:ucid>"));
	// Parameterable p = factory.createParameterable(ucid);
	// key = p.getValue();
	// }
	//
	// } catch (Exception e) {
	// // TODO Auto-generated catch block
	// e.printStackTrace();
	// }
	//
	// System.out.println("key: " + key);
	// return key;
	// }

	@Override
	protected void doRequest(SipServletRequest request) throws ServletException, IOException {
		CallStateHandler handler = null;

		handler = (CallStateHandler) request.getSession().getAttribute(CallStateHandler.CALL_STATE_HANDLER);

		if (handler == null) {
			switch (SipMethod.valueOf(request.getMethod())) {

			case INVITE:

				boolean siprec = false;
				ListIterator<String> requires = request.getHeaders("Require");
				while (requires.hasNext()) {
					if (requires.next().equals("siprec")) {
						siprec = true;
						break;
					}
				}

				if (siprec == true) {
					handler = new StartRecording();
				} else {
					// standard b2bua?
				}
				break;
			case BYE:
				String disposition = request.getHeader("Content-Disposition");
				if (disposition != null && disposition.equals("recording-session")) {

					handler = new EndRecording();
				} else {
					// handler = new EndAudioCall();
				}

				break;

			}

		}

		if (handler != null) {
			handler.printInboundMessage(request);
			try {
				handler.processEvent(request, null, null);
			} catch (Exception e) {
				e.printStackTrace();
				// throw new ServletException(e);
			}
		}

	}

	@Override
	protected void doResponse(SipServletResponse response) throws ServletException, IOException {
		CallStateHandler handler = null;

		handler = (CallStateHandler) response.getSession().getAttribute(CallStateHandler.CALL_STATE_HANDLER);
		handler.printInboundMessage(response);

		try {
			handler.processEvent(null, response, null);
		} catch (Exception e) {
			e.printStackTrace();
			// throw new ServletException(e);
		}

	}

	@Override
	public void timeout(ServletTimer timer) {

		CallStateHandler handler;

		try {
			handler = (CallStateHandler) timer.getInfo();
			handler.printTimer(timer);
			handler.processEvent(null, null, timer);
		} catch (Exception e) {
			e.printStackTrace();

		}
	}

	public String getParameter(SipServletContextEvent event, String name) {
		String value = System.getProperty(name);
		value = (value != null) ? value : event.getServletContext().getInitParameter(name);
		return value;
	}

	@Override
	public void servletInitialized(SipServletContextEvent event) {
		System.out.println("siprec-manager initialized...");

		String name;
		String value;
		Enumeration<String> parameterNames = event.getServletContext().getInitParameterNames();
		while (parameterNames.hasMoreElements()) {
			name = parameterNames.nextElement();
			if (name.contains("dial_plan")) {
				value = event.getServletContext().getInitParameter(name);
				System.out.println("adding dial plans: " + value);
				dialPlans.addElement(new VrspPair(value));
			}
		}

	}

	@Override
	public void sessionCreated(SipApplicationSessionEvent event) {
		if (logger.isLoggable(Level.FINE)) {
			String output = CallStateHandler.hexHash(event.getApplicationSession()) + " created";
			logger.fine(output);
			System.out.println(output);
		}

	}

	@Override
	public void sessionDestroyed(SipApplicationSessionEvent event) {
		if (logger.isLoggable(Level.FINE)) {
			String output = CallStateHandler.hexHash(event.getApplicationSession()) + " destroyed";
			logger.fine(output);
			System.out.println(output);
		}
	}

	@Override
	public void sessionExpired(SipApplicationSessionEvent event) {
		if (logger.isLoggable(Level.FINE)) {
			String output = CallStateHandler.hexHash(event.getApplicationSession()) + " expired";
			logger.fine(output);
			System.out.println(output);
		}
	}

	@Override
	public void sessionReadyToInvalidate(SipApplicationSessionEvent event) {
		if (logger.isLoggable(Level.FINE)) {
			String output = CallStateHandler.hexHash(event.getApplicationSession()) + " ready to invalidate";
			logger.fine(output);
			System.out.println(output);
		}
	}

}
