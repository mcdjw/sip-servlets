package oracle.communications.talkbac;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Test {

	public static void main(String[] args) {
		String sipFrag = "SIP/2.0 200 OK";
		
		System.out.println( sipFrag.substring(8, 11) );
		System.out.println( sipFrag.substring(12) );
		
	}

}
