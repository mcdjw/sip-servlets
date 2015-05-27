package oracle.communications.sdp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.activation.DataSource;
import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletMessage;
import javax.servlet.sip.SipServletMessage.HeaderForm;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;
import javax.servlet.sip.SipURI;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;

import weblogic.kernel.KernelLogManager;

import com.bea.wcp.sip.engine.server.header.HeaderUtils;

public abstract class CallStateHandler implements Serializable {
	static Logger logger;
	{
		logger = Logger.getLogger(CallStateHandler.class.getName());
		logger.setParent(KernelLogManager.getLogger());
	}

	public enum SipMethod {
		INVITE, ACK, BYE, CANCEL, OPTIONS, REGISTER, PRACK, SUBSCRIBE, NOTIFY, PUBLISH, INFO, REFER, MESSAGE, UPDATE
	}

	public final static String CALL_STATE_HANDLER = "CALL_STATE_HANDLER";
	public final static String ACTIVE_VSRP_SESSION_ID = "ACTIVE_VSRP_SESSION_ID";
	public final static String INACTIVE_VSRP_SESSION_ID = "INACTIVE_VSRP_SESSION_ID";

	protected int state = 1;

	CallStateHandler() {

	}

	CallStateHandler(CallStateHandler that) {
		this.state = that.state;
	}

	public abstract void processEvent(SipServletRequest request, SipServletResponse response, ServletTimer timer) throws Exception;

	protected String getPrintableName() {
		String name = this.getClass().getSimpleName();
		name = name.concat(" ");
		name = name.concat(Integer.toString(this.state));

		int spaces = 20 - name.length();
		for (int i = 0; i < spaces; i++) {
			name = name.concat(" ");
		}
		return name;
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

	public void printOutboundMessage(SipServletMessage message) throws UnsupportedEncodingException, IOException {
		if (logger.isLoggable(Level.FINE)) {

			try {

				if (message != null) {

					String event = message.getHeader("Event");
					if (event != null && event.equals("refer")) {
						event += " " + new String((byte[]) message.getContent()).trim();
					}
					if (event == null) {
						event = (message.getContent() != null) ? "w/ SDP" : "w/o SDP";
					}

					if (message instanceof SipServletRequest) {
						SipServletRequest rqst = (SipServletRequest) message;

						if (rqst.getMethod().equals("MESSAGE")) {
							ObjectMapper objectMapper = new ObjectMapper();
							JsonNode rootNode = objectMapper.readTree(rqst.getContent().toString());
							event = rootNode.path("event").asText();
							event += " " + rootNode.path("status").asInt();
							event += " " + rootNode.path("reason").asText();
						}

						String output = getPrintableName() + " " + ((SipURI) rqst.getTo().getURI()).getHost() + " <-- " + rqst.getMethod() + " " + event + ", " + hexHash(message)
								+ " " + rqst.getSession().getState().toString();

						logger.fine(output);
						System.out.println(output);

					} else {
						SipServletResponse rspn = (SipServletResponse) message;
						String output = getPrintableName() + " " + ((SipURI) rspn.getFrom().getURI()).getHost() + " <-- " + rspn.getStatus() + " " + rspn.getReasonPhrase() + " ("
								+ rspn.getMethod() + ") " + event + ", " + hexHash(message) + " " + rspn.getSession().getState().toString();

						logger.fine(output);
						System.out.println(output);

					}
				}

			} catch (Exception e) {
				logger.fine("logging error: " + e.getMessage());
				System.out.println("logging error: " + e.getMessage());
			}

		}

	}

	public void printInboundMessage(SipServletMessage message) throws UnsupportedEncodingException, IOException {
		if (logger.isLoggable(Level.FINE)) {

			try {

				String event = message.getHeader("Event");
				if (event != null && event.equals("refer")) {
					event += " " + new String((byte[]) message.getContent()).trim();
				}
				if (event == null) {
					event = (message.getContent() != null) ? "w/ SDP" : "w/o SDP";
				}

				if (message instanceof SipServletRequest) {
					SipServletRequest rqst = (SipServletRequest) message;
					String output = null;

					output = getPrintableName() + " " + ((SipURI) rqst.getFrom().getURI()).getHost() + " --> " + rqst.getMethod() + " " + event + ", " + hexHash(message) + " "
							+ rqst.getSession().getState().toString();

					logger.fine(output);
					System.out.println(output);
				} else {
					SipServletResponse rspn = (SipServletResponse) message;
					String output = getPrintableName() + " " + ((SipURI) rspn.getTo().getURI()).getHost() + " --> " + rspn.getStatus() + " " + rspn.getReasonPhrase() + " ("
							+ rspn.getMethod() + ") " + event + ", " + hexHash(message) + " " + rspn.getSession().getState().toString();
					logger.fine(output);
					System.out.println(output);
				}

			} catch (Exception e) {
				logger.fine("logging error: " + e.getMessage());
				System.out.println("logging error: " + e.getMessage());
			}

		}
	}

	public void printTimer(ServletTimer timer) {
		if (logger.isLoggable(Level.FINE)) {

			try {

				String output = getPrintableName() + " " + " timer id: " + timer.getId() + ", time remaining: " + (int) timer.getTimeRemaining() / 1000 + ", "
						+ hexHash(timer.getApplicationSession());

				logger.fine(output);
				// System.out.println(output);

			} catch (Exception e) {
				logger.fine("logging error: " + e.getMessage());
				// System.out.println("logging error: " + e.getMessage());
			}

		}
	}

	public static void copyHeaders(SipServletMessage origin, SipServletMessage destination) throws UnsupportedEncodingException, IOException {

		String headerName, headerValue;
		Iterator<String> itr = origin.getHeaderNames();
		while (itr.hasNext()) {
			headerName = itr.next();

			if (false == HeaderUtils.isSystemHeader(headerName, true)) {
				destination.setHeaderForm(HeaderForm.LONG);
				// destination.setHeaderForm(origin.getHeaderForm());
				ListIterator<String> headers = origin.getHeaders(headerName);

				while (headers.hasNext()) {
					headerValue = headers.next();

					if (HeaderUtils.isUnique(headerName)) {
						destination.setHeader(headerName, headerValue);
					} else {
						destination.addHeader(headerName, headerValue);
					}
				}
			}
		}

	}

	public static Multipart getMultipart(byte[] content, String contentType) throws MessagingException {
		DataSource ds = new ByteArrayDataSource(content, contentType);
		Multipart multipart = new MimeMultipart(ds);
		return multipart;
	}

	public static Multipart createMultipart() throws MessagingException {
		return new MimeMultipart("mixed");
	}

	public static String getBodyPart(Multipart multipart, String contentType) throws MessagingException, IOException {
		String body = null;

		for (int i = 0; i < multipart.getCount(); i++) {
			BodyPart bp = multipart.getBodyPart(i);
			if (contentType.equalsIgnoreCase(bp.getContentType())) {
				BufferedReader reader = new BufferedReader(new InputStreamReader((InputStream) bp.getContent()));
				StringBuilder strOut = new StringBuilder();
				String line;
				while ((line = reader.readLine()) != null) {
					strOut.append(line).append("\r\n");
				}
				body = strOut.toString();
				reader.close();
				break;
			}

		}
		return body;
	}

	public static void setBodyPart(Multipart multipart, byte[] content, String contentType) throws MessagingException {
		String disposition = null;

		for (int i = 0; i < multipart.getCount(); i++) {
			BodyPart bp = multipart.getBodyPart(i);
			if (contentType.equalsIgnoreCase(bp.getContentType())) {
				disposition = bp.getDisposition();
				multipart.removeBodyPart(i);

				break;
			}
		}

		InternetHeaders ih1 = new InternetHeaders();
		ih1.setHeader("Content-Type", contentType);
		BodyPart bodyPart = new MimeBodyPart(ih1, content);
		if (disposition != null) {
			bodyPart.setDisposition(disposition);
		}

		multipart.addBodyPart(bodyPart);

	}

}
