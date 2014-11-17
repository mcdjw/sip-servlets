package oracle.communications.talkbac;

import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;

public class TalkBACMessage {

	ObjectNode objectNode;
	public SipServletRequest message;

	public TalkBACMessage(SipApplicationSession appSession, String event) {
		ObjectMapper objectMapper = new ObjectMapper();
		objectNode = objectMapper.createObjectNode();
		setParameter("request_id", appSession.getId());
		setParameter("event", event);
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

	@Override
	public String toString() {
		return objectNode.toString();
	}

}
