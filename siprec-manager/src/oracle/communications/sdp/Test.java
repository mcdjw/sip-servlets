package oracle.communications.sdp;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.activation.DataSource;
import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

public class Test {

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

	public static void main(String[] args) throws MessagingException, IOException {

		String sdp = "" + "v=0\r\n" + "o=- 0 0 IN IP4 10.173.99.254\r\n" + "s=-\r\n" + "c=IN IP4 10.173.99.231\r\n" + "t=0 0\r\n" + "m=audio 23628 RTP/AVP 18 0 2 100\r\n"
				+ "a=rtpmap:18 G729/8000\r\n" + "a=rtpmap:0 PCMU/8000\r\n" + "a=rtpmap:2 G726-32/8000\r\n" + "a=rtpmap:100 telephone-event/8000\r\n" + "a=fmtp:100 0-15\r\n"
				+ "a=maxptime:30\r\n" + "a=label:553654187\r\n" + "a=sendonly\r\n" + "m=audio 13432 RTP/AVP 18 100\r\n" + "a=rtpmap:18 G729/8000\r\n"
				+ "a=rtpmap:100 telephone-event/8000\r\n" + "a=fmtp:100 0-15\r\n" + "a=label:553654188\r\n" + "a=sendonly\r\n";

		String recording = "" + "<?xml version='1.0' encoding='UTF-8'?>\r\n" + "<recording xmlns='urn:ietf:params:xml:ns:recording'>\r\n" + "<datamode>complete</datamode>\r\n"
				+ "<session id=\"jWBTU+dDQeZL6U3Gdk6Cwg==\">\r\n" + "<associate-time>2015-04-28T10:40:29</associate-time>\r\n"
				+ "<extensiondata xmlns:apkt=\"http:/acmepacket.com/siprec/extensiondata\">\r\n" + "<apkt:ucid>00FA0800012CAB553FA9ED;encoding=hex</apkt:ucid>\r\n"
				+ "<apkt:callerOrig>true</apkt:callerOrig>\r\n" + "</extensiondata>\r\n" + "</session>\r\n"
				+ "<participant id=\"jXXFR+0mQ1RPK5QGlWUprQ==\" session=\"jWBTU+dDQeZL6U3Gdk6Cwg==\">\r\n" + "<nameID aor=\"sip:5139316000@att.int\">\r\n"
				+ "<name>5139316000</name>\r\n" + "</nameID>" + "<send>goO9dQk7T/VclQNR6C6nrw==</send>\r\n" + "<associate-time>2015-04-28T10:40:29</associate-time>\r\n"
				+ "<extensiondata xmlns:apkt=\"http://acmepacket.com/siprec/extensiondata\">\r\n" + "<apkt:callingParty>true</apkt:callingParty>\r\n" + "</extensiondata>\r\n"
				+ "</participant>\r\n" + "<participant id=\"wpjlMwVqTUZSI2tmcXkZFg==\" session=\"jWBTU+dDQeZL6U3Gdk6Cwg==\">\r\n" + "<nameID aor=\"sip:8888483375@uhc.com\">\r\n"
				+ "<name>8888483375</name>\r\n" + "</nameID>\r\n" + "<send>Rg+mboP0TOpzFgtWlWVeUQ==</send>\r\n" + "<associate-time>2015-04-28T10:40:29</associate-time>\r\n"
				+ "<extensiondata xmlns:apkt=\"http://acmepacket.com/siprec/extensiondata\">\r\n" + "<apkt:callingParty>false</apkt:callingParty>\r\n" + "</extensiondata>\r\n"
				+ "</participant>\r\n" + "<stream id=\"goO9dQk7T/VclQNR6C6nrw==\" session=\"jWBTU+dDQeZL6U3Gdk6Cwg==\">\r\n" + "<label>553654187</label>\r\n"
				+ "<mode>separate</mode>\r\n" + "<associate-time>2015-04-28T10:40:29</associate-time>\r\n" + "</stream>\r\n"
				+ "<stream id=\"Rg+mboP0TOpzFgtWlWVeUQ==\" session=\"jWBTU+dDQeZL6U3Gdk6Cwg==\">\r\n" + "<label>553654188</label>\r\n" + "<mode>separate</mode>\r\n"
				+ "<associate-time>2015-04-28T10:40:29</associate-time>\r\n" + "</stream>\r\n" + "</recording>\r\n";

		// String ucid =
		// recording.substring(recording.indexOf("<apkt:ucid>")+11,
		// recording.indexOf("</apkt:ucid>"));
		// System.out.println(ucid);

		// Content-Type: multipart/mixed; boundary=unique-boundary-1
		// Content-Length: 2241
		// MIME-Version: 1.0

		Multipart multipart = createMultipart();
		setBodyPart(multipart, sdp.getBytes(), "application/sdp");
		setBodyPart(multipart, recording.getBytes(), "application/rs-metadata+xml");
		
		String contentType = multipart.getContentType();
		
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		multipart.writeTo(os);
		String body = new String(os.toByteArray());

		Multipart multipart2 = getMultipart(body.getBytes(), contentType);

		String sdp2 = getBodyPart(multipart2, "application/sdp");
		System.out.println(sdp2);
		System.out.println();

		String recording2 = getBodyPart(multipart2, "application/rs-metadata+xml");
		System.out.println(recording2);
		System.out.println();
	
		sdp2 = sdp2.replace("a=sendonly", "a=inactive");
		
		setBodyPart(multipart2, sdp2.getBytes(), "application/sdp");
		
		System.out.println("--------------------------");
		
		multipart2.writeTo(System.out);
		
		
		
	}

}
