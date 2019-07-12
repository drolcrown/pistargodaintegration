package br.unb.cic.goda.rtgoretoprism.generator.kl;

import br.unb.cic.goda.model.Actor;
import br.unb.cic.goda.model.Goal;
import br.unb.cic.goda.model.Plan;
import br.unb.cic.goda.rtgoretoprism.model.kl.*;
import br.unb.cic.goda.rtgoretoprism.util.NameUtility;

import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AgentDefinition {

    public LinkedList<GoalContainer> rootlist = new LinkedList<>();
    public Hashtable<String, PlanContainer> planbase;
    private String agentname;
    protected Hashtable<String, SoftgoalContainer> softgoalbase;
    protected Hashtable<String, GoalContainer> goalbase;

    public AgentDefinition(Actor a) {
        softgoalbase = new Hashtable<>();
        goalbase = new Hashtable<>();
        planbase = new Hashtable<>();
        agentname = NameUtility.adjustName(a.getName());
    }

    public static void checkOrder(String name) {
    	int i;
    	boolean valid = false;
    	String id = name.split("\\:")[0];
    	//checks if there is a runtime annotation
    	if(name.indexOf("[") != -1 && name.indexOf("]") != -1) {
    		int startofRtRegex = name.indexOf("[");
    		int endofElId = name.indexOf(":");   
    		for(i=endofElId+1;i<startofRtRegex;i++) {
    			/*checks if there is at least one letter or digit between the ":"
    			and the "["  ///// it's assumed here that the first occurrence of
    			"[" is the start of the runtime annotation*/
    			if(Character.isLetterOrDigit(name.charAt(i))) {
    				valid = true;
    				break;
    			}
    		}
    		if(!valid) {
    			throw new Error("Label must be in the following order: (G|T)#: description [runtime annotation]. Error found at: " + id);
    		}
    		//checks if there is any char or letter after "]"
    		if(name.indexOf("]")+1 != name.length()) {
    			throw new Error("There must not be anything written after the runtime annotation. Error found at: " + id);
    		}
    	}
    	else {
    	//checks the existence of a "[" that is not closed
    		if(name.indexOf("[") != -1 && name.indexOf("]") == -1) {
    			throw new Error("Closing bracket is missing. Error found at: " + id);
    		}
    		else {
    			if(name.indexOf("[") == -1 && name.indexOf("]") != -1) {
        			throw new Error("Open bracket is missing. Error found at: " + id);
        		}
    		}
    	}
    }
    public static String parseElId(String name) {
    	checkOrder(name);
        String patternString = "(^[GT]\\d+\\.?\\d*):";
        Pattern pattern = Pattern.compile(patternString);
        java.util.regex.Matcher matcher = pattern.matcher(name);
        if (matcher.find())
            return matcher.group(1);
        else
        	throw new Error("Label is incorrect.");
            //return null;
    }

    private static String parseRTRegex(String name) {
        String patternString = "\\[(.*)\\]";
        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(name);
        if (matcher.find())
            return matcher.group(1);
        else
            return null;
    }

    public void addRootGoal(GoalContainer rootgoal) {
        rootlist.add(rootgoal);
    }

    public GoalContainer createGoal(Goal goal, Const type) {
        GoalContainer gc = new GoalContainer(goal, type);
        setRTAttributes(gc);
        if (goalbase.containsKey(gc.getName()))
            return goalbase.get(gc.getName());
        goalbase.put(gc.getName(), gc);
        return gc;
    }

    public PlanContainer createPlan(Plan p) {
        PlanContainer pc = new PlanContainer(p);
        setRTAttributes(pc);
        if (planbase.containsKey(pc.getName()))
            return planbase.get(pc.getName());
        planbase.put(pc.getName(), pc);
        return pc;
    }

    public boolean containsGoal(Goal goal) {
        ElementContainer gc = new ElementContainer(goal);
        return goalbase.containsKey(gc.getName());
    }

    public boolean containsPlan(Plan plan) {
        ElementContainer pc = new ElementContainer(plan);
        return planbase.containsKey(pc.getName());
    }

    public String getAgentName() {
        return agentname;
    }

    public List<GoalContainer> getRootGoalList() {
    	return rootlist;
    }

    private void setRTAttributes(RTContainer gc) {
        gc.setElId(parseElId(gc.getName()));
        gc.setRtRegex(parseRTRegex(gc.getName()));
    }
}