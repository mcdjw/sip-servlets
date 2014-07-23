package vorpal.sip.servlets.jsr289.callcontrol;

public class TestCase {

	public static void main(String[] args) {
		
		
		
		char digit;
		String digits = "123456789#";
		
		while(digits.length()>0){
			digit = digits.charAt(0);
			System.out.println(digit);
			digits = digits.substring(1);
		}
		
		
		
		
	}

}
