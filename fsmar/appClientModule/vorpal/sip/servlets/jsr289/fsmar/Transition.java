/* 
 * COPYRIGHT: VORPAL.ORG, 2014
 * AUTHOR:    JEFF@MCDONALD.NET
 */

package vorpal.sip.servlets.jsr289.fsmar;

import java.util.StringTokenizer;

public class Transition {
	public Condition condition;
	public Action action;
	public String previous;
	public String next;
	public String key;
	public String value;

	public Transition(String key, String value) {
		this.key = key;
		this.value = value;

		StringTokenizer st = new StringTokenizer(value, ",");
		this.previous = st.nextToken().trim();
		String strCondition = st.nextToken().trim();
		String strAction = st.nextToken().trim();
		this.next = st.nextToken().trim();
		
		this.condition = new Condition(strCondition);
		this.action = new Action(this.next, strAction);
	}

}
