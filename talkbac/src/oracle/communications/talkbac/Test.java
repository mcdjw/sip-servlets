package oracle.communications.talkbac;

public class Test {

	public static void main(String[] args) {
		// TODO Auto-generated method stub

		
//		String auth = "SID username=\"jeff@mcdonald.net\",realm=\"null\",cnonce=\"0f9681da257b88ec7428a8b8b3c18d71\",nc=00000001,qop=auth,opaque=\"1234\",uri=\"sip:192.168.1.202:5060\",nonce=\"null\",response=\"7be61f1b44cd8d81d089d90e0365222e\",algorithm=null";
//		
//		
//		String strUsername = "username=\"";
//		int begin = auth.indexOf(strUsername, 0);
//		begin = begin+strUsername.length();
//		int end = auth.indexOf("\"", begin);
//		String username = auth.substring(begin, end);
//		System.out.println(username);
		
		
		String kpmlResponse = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><kpml-response version=\"1.0\" code=\"200\" text=\"OK\" digits=\"123\" tag=\"dtmf\"/>";
		

		String begin = "digits=\"";
		String end = "\"";

		int beginIndex = kpmlResponse.indexOf(begin) + begin.length();
		int endIndex = kpmlResponse.indexOf(end, beginIndex);

		String digits = kpmlResponse.substring(beginIndex, endIndex);
		
		System.out.println(digits);
		
		
	}

}
