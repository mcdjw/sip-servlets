package oracle.communications.talkbac;


public class Test {

	public static void main(String[] args) {
		// String blackhole = ""
		// + "v=0\r\n"
		// + "o=- 15474517 1 IN IP4 192.168.1.202\r\n"
		// + "s=cpc_med\r\n"
		// + "c=IN IP4 192.168.1.202\r\n"
		// + "t=0 0\r\n"
		// + "m=audio 23348 RTP/AVP 0\r\n"
		// + "a=rtpmap:0 pcmu/8000\r\n"
		// + "a=sendrecv \r\n";
		//
		//
		// String newSDP = blackhole.replaceFirst("c=.*", "c=IN IP4 0.0.0.0");
		//
		// System.out.println(newSDP);

		System.out.println("case "+ "Via".hashCode() + ": //Via");
		System.out.println("case "+ "From".hashCode() + ": //From");
		System.out.println("case "+ "To".hashCode() + ": //To");
		System.out.println("case "+ "Call-ID".hashCode() + ": //Call-ID");
		System.out.println("case "+ "Route".hashCode() + ": //Route");
		System.out.println("case "+ "CSeq".hashCode() + ": //CSeq");
		System.out.println("case "+ "Call-Info".hashCode() + ": //Call-Info");
		System.out.println("case "+ "Contact".hashCode() + ": //Contact");
		System.out.println("case "+ "Content-Type".hashCode() + ": //Content-Type");
		System.out.println("case "+ "Content-Length".hashCode() + ": //Content-Length");

	}
}
