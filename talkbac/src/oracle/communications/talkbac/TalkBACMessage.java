package oracle.communications.talkbac;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.logging.Logger;

import javax.servlet.sip.Address;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletMessage;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;

import weblogic.kernel.KernelLogManager;

public class TalkBACMessage {
	static Logger logger;
	{
		logger = Logger.getLogger(Authentication.class.getName());
		logger.setParent(KernelLogManager.getLogger());
	}

	ObjectNode objectNode;
	public SipServletRequest message;

	public TalkBACMessage(SipApplicationSession appSession, String event) {
		String requestId = (String) appSession.getAttribute(TalkBACSipServlet.REQUEST_ID);

		ObjectMapper objectMapper = new ObjectMapper();
		objectNode = objectMapper.createObjectNode();

		setParameter("request_id", requestId);
		setParameter("event", event);

		Address client_address = (Address) appSession.getAttribute(TalkBACSipServlet.CLIENT_ADDRESS);
		Address application_address = (Address) appSession.getAttribute(TalkBACSipServlet.APPLICATION_ADDRESS);

		message = TalkBACSipServlet.factory.createRequest(appSession, "MESSAGE", application_address, client_address);
	}

	public void setStatus(int status, String reason) {
		setParameter("status", Integer.toString(status));
		setParameter("reason", reason);
	}

	public void setParameter(String name, String value) {
		if (value != null) {
			objectNode.put(name, value);
		}
	}

	public void setParameter(String name, char value) {
		StringBuilder strValue = new StringBuilder();
		strValue.append(value);
		objectNode.put(name, strValue.toString());
	}

	public String getParameter(String name) {
		return objectNode.get(name).asText();
	}

	public String getReason() {
		return objectNode.get("reason").asText();
	}

	public int getStatus() {
		return Integer.parseInt(objectNode.get("reason").asText());
	}

	public void send() throws IOException {
		TalkBACSipServlet.cdr.info(objectNode.toString().replaceAll("[\n\r]", ""));

		message.setContent(objectNode.toString(), "text/plain");
		message.send();
		printOutboundMessage(message);

	}

	public void printOutboundMessage(SipServletMessage message) throws UnsupportedEncodingException, IOException {

		if (message instanceof SipServletRequest) {
			SipServletRequest rqst = (SipServletRequest) message;
			logger.fine(" <== " + this.getClass().getSimpleName() + " " + rqst.getMethod() + " " + rqst.getTo());

		} else {
			SipServletResponse rspn = (SipServletResponse) message;
			logger.fine(" <== " + this.getClass().getSimpleName() + " " + rspn.getMethod() + rspn.getReasonPhrase() + rspn.getTo());
		}

	}

}
