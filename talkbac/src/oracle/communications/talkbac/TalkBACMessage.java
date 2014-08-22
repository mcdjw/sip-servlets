package oracle.communications.talkbac;

import java.io.IOException;

import javax.servlet.sip.Address;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;

public class TalkBACMessage {
	ObjectNode objectNode;
	SipServletRequest message;

	public TalkBACMessage(SipApplicationSession appSession, String event) {

		ObjectMapper objectMapper = new ObjectMapper();
		objectNode = objectMapper.createObjectNode();

		String requestId = (String) appSession.getAttribute(TalkBACSipServlet.REQUEST_ID);

		setParameter("request_id", requestId);
		setParameter("event", event);

		Address client_address = (Address) appSession.getAttribute(TalkBACSipServlet.CLIENT_ADDRESS);
		Address application_address = (Address) appSession.getAttribute(TalkBACSipServlet.APPLICATION_ADDRESS);

		message = TalkBACSipServlet.factory.createRequest(appSession, "MESSAGE", application_address, client_address);
	}

	public void setStatus(int status, String reason){
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
	
	public String getReason(){
		return objectNode.get("reason").asText();
	}
	
	public int getStatus(){
		return Integer.parseInt( objectNode.get("reason").asText() );
	}

	public void send() throws IOException {
		TalkBACSipServlet.cdr.info(objectNode.toString().replaceAll("[\n\r]", ""));

		message.setContent(objectNode.toString(), "text/plain");
		message.send();
	}

}
