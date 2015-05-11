package oracle.communications.sdp;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import javax.activation.DataSource;
import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;

public class Test {
	
	
	

	public static void main(String[] args) throws MessagingException, IOException {

		String sdp = "" + "v=0\r\n" + "o=- 0 0 IN IP4 10.173.99.254\r\n" + "s=-\r\n" + "c=IN IP4 10.173.99.231\r\n"
				+ "t=0 0\r\n" + "m=audio 23628 RTP/AVP 18 0 2 100\r\n" + "a=rtpmap:18 G729/8000\r\n"
				+ "a=rtpmap:0 PCMU/8000\r\n" + "a=rtpmap:2 G726-32/8000\r\n" + "a=rtpmap:100 telephone-event/8000\r\n"
				+ "a=fmtp:100 0-15\r\n" + "a=maxptime:30\r\n" + "a=label:553654187\r\n" + "a=sendonly\r\n"
				+ "m=audio 13432 RTP/AVP 18 100\r\n" + "a=rtpmap:18 G729/8000\r\n"
				+ "a=rtpmap:100 telephone-event/8000\r\n" + "a=fmtp:100 0-15\r\n" + "a=label:553654188\r\n"
				+ "a=sendonly\r\n";

		String recording = "" + "<?xml version='1.0' encoding='UTF-8'?>\r\n"
				+ "<recording xmlns='urn:ietf:params:xml:ns:recording'>\r\n" + "<datamode>complete</datamode>\r\n"
				+ "<session id=\"jWBTU+dDQeZL6U3Gdk6Cwg==\">\r\n"
				+ "<associate-time>2015-04-28T10:40:29</associate-time>\r\n"
				+ "<extensiondata xmlns:apkt=\"http:/acmepacket.com/siprec/extensiondata\">\r\n"
				+ "<apkt:ucid>00FA0800012CAB553FA9ED;encoding=hex</apkt:ucid>\r\n"
				+ "<apkt:callerOrig>true</apkt:callerOrig>\r\n" + "</extensiondata>\r\n" + "</session>\r\n"
				+ "<participant id=\"jXXFR+0mQ1RPK5QGlWUprQ==\" session=\"jWBTU+dDQeZL6U3Gdk6Cwg==\">\r\n"
				+ "<nameID aor=\"sip:5139316000@att.int\">\r\n" + "<name>5139316000</name>\r\n" + "</nameID>"
				+ "<send>goO9dQk7T/VclQNR6C6nrw==</send>\r\n"
				+ "<associate-time>2015-04-28T10:40:29</associate-time>\r\n"
				+ "<extensiondata xmlns:apkt=\"http://acmepacket.com/siprec/extensiondata\">\r\n"
				+ "<apkt:callingParty>true</apkt:callingParty>\r\n" + "</extensiondata>\r\n" + "</participant>\r\n"
				+ "<participant id=\"wpjlMwVqTUZSI2tmcXkZFg==\" session=\"jWBTU+dDQeZL6U3Gdk6Cwg==\">\r\n"
				+ "<nameID aor=\"sip:8888483375@uhc.com\">\r\n" + "<name>8888483375</name>\r\n" + "</nameID>\r\n"
				+ "<send>Rg+mboP0TOpzFgtWlWVeUQ==</send>\r\n"
				+ "<associate-time>2015-04-28T10:40:29</associate-time>\r\n"
				+ "<extensiondata xmlns:apkt=\"http://acmepacket.com/siprec/extensiondata\">\r\n"
				+ "<apkt:callingParty>false</apkt:callingParty>\r\n" + "</extensiondata>\r\n" + "</participant>\r\n"
				+ "<stream id=\"goO9dQk7T/VclQNR6C6nrw==\" session=\"jWBTU+dDQeZL6U3Gdk6Cwg==\">\r\n"
				+ "<label>553654187</label>\r\n" + "<mode>separate</mode>\r\n"
				+ "<associate-time>2015-04-28T10:40:29</associate-time>\r\n" + "</stream>\r\n"
				+ "<stream id=\"Rg+mboP0TOpzFgtWlWVeUQ==\" session=\"jWBTU+dDQeZL6U3Gdk6Cwg==\">\r\n"
				+ "<label>553654188</label>\r\n" + "<mode>separate</mode>\r\n"
				+ "<associate-time>2015-04-28T10:40:29</associate-time>\r\n" + "</stream>\r\n" + "</recording>\r\n";

		
		String ucid = recording.substring(recording.indexOf("<apkt:ucid>")+11, recording.indexOf("</apkt:ucid>"));
		System.out.println(ucid);
		
		
		
//		Multipart multipart = new MimeMultipart("mixed");
//
//		BodyPart sdpBodyPart = new MimeBodyPart();
//		sdpBodyPart.setContent(sdp, "application/sdp");
//		sdpBodyPart.setHeader("Content-Type", "application/sdp");
//		multipart.addBodyPart(sdpBodyPart);
//
//		BodyPart recordignBodyPart = new MimeBodyPart();
//		recordignBodyPart.setContent(recording, "application/rs-metadata+xml");
//		recordignBodyPart.setHeader("Content-Type", "application/rs-metadata+xml");
//		recordignBodyPart.setDisposition("recording-session");
//		multipart.addBodyPart(recordignBodyPart);
//
//		ByteArrayOutputStream os = new ByteArrayOutputStream();
//		multipart.writeTo(os);
//
//		String body = new String(os.toByteArray());
//
//		DataSource ds = new ByteArrayDataSource(os.toByteArray(), multipart.getContentType());
//		Multipart multipart2 = new MimeMultipart(ds);
//
//		BodyPart bodyPart;
//		
//		System.out.println("+++++++++++++++++++");
//		
//		for (int i = 0; i < multipart2.getCount(); i++) {
//			ByteArrayOutputStream os2 = new ByteArrayOutputStream();
//			bodyPart = multipart2.getBodyPart(i);
//						
//			System.out.println( bodyPart.getHeader("Content-Type")[0] );
//			
//			
//			bodyPart.writeTo(os2);
//			
//			
////			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
////			DocumentBuilder db = dbf.newDocumentBuilder(); 
////			Document doc = db.parse(       );
//
//			
//			System.out.println(os2);
//			System.out.println("+++++++++++++++++++");
//			
//			
			
//		}

	}

}
