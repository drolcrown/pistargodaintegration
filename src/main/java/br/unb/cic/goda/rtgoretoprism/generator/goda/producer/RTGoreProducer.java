package br.unb.cic.goda.rtgoretoprism.generator.goda.producer;

import br.unb.cic.goda.model.Actor;
import br.unb.cic.goda.model.GeneralEntity;
import br.unb.cic.goda.model.Goal;
import br.unb.cic.goda.model.Plan;
import br.unb.cic.goda.rtgoretoprism.generator.CodeGenerationException;
import br.unb.cic.goda.rtgoretoprism.generator.goda.parser.RTParser;
import br.unb.cic.goda.rtgoretoprism.generator.goda.writer.PrismWriter;
import br.unb.cic.goda.rtgoretoprism.generator.kl.AgentDefinition;
import br.unb.cic.goda.rtgoretoprism.model.kl.Const;
import br.unb.cic.goda.rtgoretoprism.model.kl.GoalContainer;
import br.unb.cic.goda.rtgoretoprism.model.kl.PlanContainer;
import br.unb.cic.goda.rtgoretoprism.model.kl.RTContainer;
import br.unb.cic.goda.rtgoretoprism.util.kl.TroposNavigator;

import java.io.IOException;
import java.util.*;

public class RTGoreProducer {
	
    private TroposNavigator tn;
    private String inputFolder;
    private String outputFolder;
    private Set<Actor> allActors;
    private Set<Goal> allGoals;

    private Map<String, Boolean[]> rtSortedGoals;
    private Map<String, Object[]> rtCardGoals;
    private Map<String, Set<String>> rtAltGoals;
    private Map<String, String[]> rtTryGoals;
    private Map<String, Boolean> rtOptGoals;

    public RTGoreProducer(Set<Actor> allActors, Set<Goal> allGoals, String in, String out) {
        tn = new TroposNavigator();
        this.inputFolder = in;
        this.outputFolder = out;
        this.allActors = allActors;
        this.allGoals = allGoals;
        this.rtSortedGoals = new TreeMap<>();
        this.rtCardGoals = new TreeMap<>();
        this.rtAltGoals = new TreeMap<>();
        this.rtTryGoals = new TreeMap<>();
        this.rtOptGoals = new TreeMap<>();
    }

    public AgentDefinition run() throws CodeGenerationException, IOException {
        System.out.println("Starting PRISM Model Generation Process (Knowledge Level)");
        System.out.println("\tTemplate Input Folder: " + inputFolder);
        System.out.println("\tOutput Folder: " + outputFolder);
        long startTime = new Date().getTime();
        AgentDefinition ad = null;
        for (Actor a : allActors) {
            System.out.println("Generating DTMC model for: " + a.getName());
            ad = new AgentDefinition(a);
            for (Goal rootgoal : tn.getRootGoals(a)) {
                Const type = Const.ACHIEVE;
                Const request = Const.NONE;
                GoalContainer gc = ad.createGoal(rootgoal, type);
                gc.setRequest(request);
                ad.addRootGoal(gc);
                addGoal(rootgoal, gc, ad, false);
            }
            checkUniqueIdentifier(a);
            List<Plan> planList = a.getPlanList();
            PrismWriter writer = new PrismWriter(ad, planList, inputFolder, outputFolder);
            writer.writeModel();
        }
        System.out.println("DTMC model created in " + (new Date().getTime() - startTime) + "ms.");
        return ad;
    }
    
    //checks whether goals have unique ids or not.
    public void checkUniqueIdentifier(Actor a) {
    	int i,j;
    	List<Goal> goallist = a.getAllGoals();
    	for(i=0;i<goallist.size();i++) {
    		String goalId = goallist.get(i).getName().split("\\:")[0];
    		for(j=0;j<goallist.size();j++) {
    			if(i != j) {
    				String otherGoalId = goallist.get(j).getName().split("\\:")[0];
    				if(goalId.contentEquals(otherGoalId)) {
    					throw new Error("Goals IDs are not unique");
    				}
    			}
    		}
    	}
    }
    
    private void addGoal(Goal g, GoalContainer gc, final AgentDefinition ad, boolean included) throws IOException {
        included = included || allGoals.isEmpty() || allGoals.contains(g);
        gc.setIncluded(included);
        String rtRegex = gc.getRtRegex();
        storeRegexResults(gc.getUid(), rtRegex, gc.getDecomposition());
        List<Goal> declist = g.getDecompositionList();
        sortIntentionalElements(declist);
        if (g.isAndDecomposition()) {
        	gc.createDecomposition(Const.AND);
        	if(g.isOrDecomposition()) {
        		String aux = g.getName().split("\\:")[0];
        		throw new Error("Error: " + aux + " is reﬁned through AND and OR-decompositions at the same time.");
        	}
        }
        else if (g.isOrDecomposition())
            gc.createDecomposition(Const.OR);
        iterateGoals(ad, gc, declist, included);
        iterateRts(gc, gc.getDecompGoals());
        iterateMeansEnds(g, gc, ad, included);
        iterateRts(gc, gc.getDecompPlans());
        checkGoalDecomposition(gc, declist);
    }
    
   public void checkGoalDecomposition(GoalContainer gc, List<Goal> declist) {
	   int i;
	   int children = 0;
	   String errorid = gc.getElId();
	   if(declist.size() > 0 && gc.getDecompPlans().size() > 0) {
		   throw new Error("Error: Goals cant be refined into tasks and subgoals at the same time. Error found at: " + errorid);
	   }
	   if(gc.getRtRegex() != null) {      
		   //Checking if goal has runtime annotation even without having any children.
		   if(declist.size() == 0 && gc.getDecompPlans().size() == 0) {
			   throw new Error("Error: Goal presents a runtime annotation but it doesnt have any children nodes. Error found at: " + errorid);
		   }
   		//checking if the goal actually has the amount of children presented in the runtime annotation
   			for(i=0;i<gc.getRtRegex().length();i++) {
   				if(gc.getRtRegex().charAt(i) == 'T' || gc.getRtRegex().charAt(i) == 'G') {
   					children++;
   				}
   			}
   			if(declist.size() == 0) {
   				if(gc.getDecompPlans().size() > 1) {
   		   			throw new Error("Error: Goals refine in just one task, not multiple. Error found at: " + errorid);
   		   		}
   				else {
   					if(children > 1) {
   						throw new Error("Error: Goals refine in just one task, not multiple. Error found at: " + errorid);
   					}
   				}
   			}
   			else {
   				if(declist.size() != children) {
   	   				throw new Error("Error: The amount of children does not match the amount of goals presented in the runtime annotation. Error found at: " + errorid);
   	   			}
   			}
	   }
	   
	   if ((gc.getDecomposition() == Const.AND || gc.getDecomposition() == Const.OR)
			   && declist.size() >= 2) {
    		if(gc.getRtRegex() == null) {
    			throw new Error("Error: Goals with 2 or more children must"
    					+ " have runtime annotation. Error found at: " + errorid);
    		}    	
    		//checking if there is a match for each goal ID in the Runtime annotation
    		for (i=0;i<declist.size();i++) {
    			int j=0,k=0;
    			boolean found = false;
    			String goalIdentifier = declist.get(i).getName().split("\\:")[0];
    			while(k < goalIdentifier.length() && j < gc.getRtRegex().length()) {
    				if(goalIdentifier.charAt(k) == gc.getRtRegex().charAt(j)) {
    					k++;
    					if(k == goalIdentifier.length()) {
    						if(j+1 == gc.getRtRegex().length()) {
    							found = true;
    							break;
    						}
    						else {
    							if(!Character.isLetterOrDigit(gc.getRtRegex().charAt(j+1))) {
    								found = true;
    								break;
    							}
    						}
    					}
    				}
    				else {
    					k=0;
    				}
    				
    				j++;
    			}
    			if(j >= gc.getRtRegex().length() || !found) {
    				throw new Error("Error: There is at least one subgoal that is not in"
    						+ " the runtime annotation. Error found at: " + errorid);
    			}
    		}
    	}
	   
	   	List<PlanContainer> planlist = gc.getDecompPlans();
	   	if(planlist.size() > 1) {
	   		throw new Error("Error: Goals refine in just one task, not multiple. Error found at: " + errorid);
	   	}
	   	else {
	   		if(planlist.size() > 0) {
		   		int k;
		   		//check if level 1 tasks were named correctly (T#)
		   		String taskId = planlist.get(0).getName().split("\\:")[0];
		   		if(planlist.get(0).getRtRegex() != null && planlist.get(0).getDecompPlans().size() == 0) {
		   	    	throw new Error("Error: Task presents a runtime annotation but it doesnt have any children nodes. Error found at: " + taskId);
		   		}
		   		for(k=0;k<taskId.length() ;k++) {
		   			if(!Character.isLetterOrDigit(taskId.charAt(k))) {
		   				throw new Error("Error: Level 1 tasks must be named accordingly (T#). Error found at: " + taskId);
		   			}
		   		}   	    
		   	}
	   	}
    }

    private void iterateGoals(AgentDefinition ad, GoalContainer gc, List<Goal> decList, boolean include) throws IOException {
        Integer prevPath = gc.getPrevTimePath();
        Integer rootFutPath = gc.getFutTimePath();
        Integer rootPath = gc.getTimePath();
        Integer rootTime = gc.getTimeSlot();
        gc.setRootTimeSlot(rootTime);
        for (Goal dec : decList) {
            boolean newgoal = !ad.containsGoal(dec);
            boolean parDec = false;
            boolean trivial = false;
            GoalContainer deccont = ad.createGoal(dec, Const.ACHIEVE);
            gc.addDecomp(deccont);
            if (rtSortedGoals.containsKey(deccont.getElId())) {
                Boolean[] decDeltaPathTime = rtSortedGoals.get(deccont.getElId());
                if (decDeltaPathTime[1]) {
                    deccont.setPrevTimePath(gc.getTimePath());
                    deccont.setFutTimePath(gc.getFutTimePath());
                    deccont.setTimePath(rootPath);
                    deccont.setTimeSlot(gc.getTimeSlot() + 1);
                } else if (decDeltaPathTime[0]) {
                    if (gc.getFutTimePath() > 0)
                        deccont.setTimePath(gc.getFutTimePath() + 1);
                    else
                        deccont.setTimePath(gc.getTimePath() + 1);
                    deccont.setTimeSlot(rootTime);
                    parDec = true;
                } else {
                    trivial = true;
                    deccont.setPrevTimePath(prevPath);
                    deccont.setFutTimePath(rootFutPath);
                    deccont.setTimePath(rootPath);
                    deccont.setTimeSlot(rootTime);
                }
            } else {
                trivial = true;
                deccont.setPrevTimePath(prevPath);
                deccont.setFutTimePath(gc.getFutTimePath());
                deccont.setTimePath(gc.getTimePath());
                deccont.setTimeSlot(gc.getTimeSlot());
            }
            if (rtCardGoals.containsKey(deccont.getElId())) {
                Object[] card = rtCardGoals.get(deccont.getElId());
                Const cardType = (Const) card[0];
                Integer cardNumber = (Integer) card[1];
                deccont.setCardType(cardType);
                if (cardType.equals(Const.SEQ))
                    deccont.setTimeSlot(deccont.getTimeSlot() + cardNumber - 1);
            }
            deccont.addFulfillmentConditions(gc.getFulfillmentConditions());
            if (newgoal) {
                addGoal(dec, deccont, ad, include);
                gc.setFutTimePath(Math.max(deccont.getTimePath(), deccont.getFutTimePath()));
                if (trivial || (!parDec && rtAltGoals.get(deccont.getElId()) == null))
                    gc.setTimeSlot(deccont.getTimeSlot());
            }
        }
    }

    private void addPlan(Plan p, PlanContainer pc, final AgentDefinition ad) throws IOException {
        storeRegexResults(pc.getUid(), pc.getRtRegex(), pc.getDecomposition());
        List<Plan> decList = p.getEndPlans();
        sortIntentionalElements(decList);
        if (p.isAndDecomposition()) {
        	pc.createDecomposition(Const.AND);
        	if(p.isOrDecomposition()) {
        		String aux = p.getName().split("\\:")[0];
        		throw new Error("Error: " + aux + " is reﬁned through AND and OR-decompositions at the same time.");
        	}
        }
        else
        	pc.createDecomposition(Const.OR);
        iteratePlans(ad, pc, decList);
        iterateRts(pc, pc.getDecompPlans());
        checkPlanDecomposition(pc, decList);
    }
    
    
    public void checkPlanDecomposition(PlanContainer pc, List<Plan> declist) {
    	int i;
    	int children=0;
    	String errorid = pc.getElId(); 
    	//verifying if subtask shares the first part of its ID with parent node.
    	if(declist.size() == 0 && pc.getRtRegex() != null) {
    		throw new Error("Error: Task presents a runtime annotation but it doesnt have any children nodes. Error found at: " + errorid);
    	}
    	else {
    		if(pc.getRtRegex() != null) {        		
        		//checking if the plan actually has the amount of children presented in the runtime annotation
        		for(i=0;i<pc.getRtRegex().length();i++) {
        			if(pc.getRtRegex().charAt(i) == 'T') {
        				children++;
        			}
        		}
        		if(declist.size() != children) {
        			throw new Error("Error: The amount of children does not match the amount of tasks presented in the runtime annotation. Error found at: " + errorid);
        		}
    		}
    	}
    	if(declist.size() >= 1) {
    		int k;
    		boolean validId = false;
    		boolean parentOnLevelOne = true;
    		String parentId = pc.getName().split("\\:")[0];
    		for(i=0;i<declist.size();i++) {
    			validId = false;
    			parentOnLevelOne = true;
    			//checks if level-2 subtasks have the proper id (T#.#)    			
    			String planIdentifier = declist.get(i).getName().split("\\:")[0];
    			for(k=0;k<planIdentifier.length();k++) {
    				if(planIdentifier.charAt(k) == '.') {
    					validId = true;
    				}
    			}
    			if(!validId) {
    				throw new Error("Error: Tasks that are not on level 1 must have T#.# form. Error found at: " + planIdentifier);
    			}
    			//////////////////////////
    			for(k=0;k<parentId.length();k++) {
    				if(parentId.charAt(k) == '.') {
    					parentOnLevelOne = false;
    					break;
    				}
    			}
    			if(!parentOnLevelOne) {
    				for(k=0;k<parentId.length();k++) {
    					if(parentId.charAt(k) != planIdentifier.charAt(k)) {
    						throw new Error("Error: Subtask must inherit first part of its ID from its parent. Error found at: " + planIdentifier);
    					}
    				}
    			}
    			else {
    				String inheritedId = planIdentifier.split("\\.")[0];
        			if(!inheritedId.contentEquals(parentId)) {
        				throw new Error("Error: subtask must inherit first part of its ID from its parent. Error found at: " + planIdentifier);
        			}
    			}
    		}
    	}
		////////////////////////////////////////////
    	if ((pc.getDecomposition() == Const.AND || pc.getDecomposition() == Const.OR) && declist.size() >= 2) {
    		
    		if(pc.getRtRegex() == null) {
    			throw new Error("Error: Tasks with 2 or more children must have runtime annotation. Error found at: " + errorid);
    		}   	
    		for (i=0;i<declist.size();i++) {
    			int j=0,k=0;
    			boolean found = false;
    			String planIdentifier = declist.get(i).getName().split("\\:")[0];
    			
    			//checking if any subtask has the same name of one of its siblings.
    			for(j=0;j<declist.size();j++)  {
    				if(i != j) {
    					if(planIdentifier.contentEquals(declist.get(j).getName().split("\\:")[0])) {
    						throw new Error("Error: at least two subtasks have the same exactly name. Error found at: " + planIdentifier);
    					}
    				}
    			}
    			j=0;
    			//checking if there is a match for each plan ID in the Runtime annotation
    			while(k < planIdentifier.length() && j < pc.getRtRegex().length()) {
    				if(planIdentifier.charAt(k) == pc.getRtRegex().charAt(j)) {
    					k++;
    					if(k == planIdentifier.length()) {
    						if(j+1 == pc.getRtRegex().length()) {
    							found = true;
    							break;
    						}
    						else {
    							if(!Character.isLetterOrDigit(pc.getRtRegex().charAt(j+1))) {
    								found = true;
    								break;
    							}
    						}
    					}
    				}
    				else {
    					k=0;
    				}
    				
    				j++;
    			}
    			if(j >= pc.getRtRegex().length() || !found) {
    				throw new Error("Error: IDs in the runtime annotation does not match subtasks IDs. Error found at: " + errorid);
    			}
    		}
    	}
    }

    private void iteratePlans(AgentDefinition ad, PlanContainer pc, List<Plan> decList) throws IOException {
        Integer prevPath = pc.getPrevTimePath();
        Integer rootFutPath = pc.getFutTimePath();
        Integer rootPath = pc.getTimePath();
        Integer rootTime = pc.getTimeSlot();
        for (Plan dec : decList) {
            boolean newplan = !ad.containsPlan(dec);
            boolean parPlan = false;
            PlanContainer deccont = ad.createPlan(dec);
            pc.addDecomp(deccont);
            if (rtSortedGoals.containsKey(deccont.getElId())) {
                Boolean[] decDeltaPathTime = rtSortedGoals.get(deccont.getElId());
                if (decDeltaPathTime[1]) {
                    deccont.setPrevTimePath(pc.getTimePath());
                    deccont.setFutTimePath(pc.getFutTimePath());
                    deccont.setTimePath(rootPath);
                    deccont.setTimeSlot(pc.getTimeSlot() + 1);
                } else if (decDeltaPathTime[0]) {
                    if (pc.getFutTimePath() > 0)
                        deccont.setTimePath(pc.getFutTimePath() + 1);
                    else
                        deccont.setTimePath(pc.getTimePath() + 1);
                    deccont.setTimeSlot(rootTime);
                    parPlan = true;
                } else {
                    deccont.setPrevTimePath(prevPath);
                    deccont.setFutTimePath(rootFutPath);
                    deccont.setTimePath(rootPath);
                    deccont.setTimeSlot(rootTime);
                }
            } else {
                deccont.setPrevTimePath(prevPath);
                deccont.setFutTimePath(pc.getFutTimePath());
                deccont.setTimePath(pc.getTimePath());
                deccont.setTimeSlot(pc.getTimeSlot());
            }
            if (rtCardGoals.containsKey(deccont.getElId())) {
                Object[] card = rtCardGoals.get(deccont.getElId());
                Const cardType = (Const) card[0];
                Integer cardNumber = (Integer) card[1];
                if (cardType.equals(Const.SEQ))
                    deccont.setTimeSlot(deccont.getTimeSlot() + cardNumber - 1);
            }
            deccont.addFulfillmentConditions(pc.getFulfillmentConditions());
            if (newplan) {
                addPlan(dec, deccont, ad);
                pc.setFutTimePath(Math.max(deccont.getTimePath(), deccont.getFutTimePath()));
                if (!parPlan && rtAltGoals.get(deccont.getElId()) == null) {
                    pc.setTimeSlot(deccont.getTimeSlot());
                }
            }
        }
    }

    private void iterateMeansEnds(Goal g, GoalContainer gc, final AgentDefinition ad, boolean included) throws IOException {
        Integer prevPath = gc.getPrevTimePath();
        Integer rootFutPath = gc.getFutTimePath();
        Integer rootPath = gc.getTimePath();
        Integer rootTime = gc.getTimeSlot();
        if (included && !g.getMeansToAnEndPlans().isEmpty()) {
            List<Plan> melist = g.getMeansToAnEndPlans();
            sortIntentionalElements(melist);
            gc.createDecomposition(Const.ME);
            for (Plan p : melist) {
                boolean newplan = !ad.containsPlan(p);
                boolean parPlan = false;
                boolean trivial = false;
                PlanContainer pc = ad.createPlan(p);
                gc.addMERealPlan(pc);
                if (rtSortedGoals.containsKey(pc.getElId())) {
                    Boolean[] decDeltaPathTime = rtSortedGoals.get(pc.getElId());
                    if (decDeltaPathTime[1]) {
                        pc.setPrevTimePath(gc.getTimePath());
                        pc.setFutTimePath(gc.getFutTimePath());
                        pc.setTimePath(rootPath);
                        pc.setTimeSlot(gc.getTimeSlot() + 1);
                    } else if (decDeltaPathTime[0]) {
                        parPlan = true;
                        pc.setPrevTimePath(prevPath);
                        if (gc.getFutTimePath() > 0)
                            pc.setTimePath(gc.getFutTimePath() + 1);
                        else
                            pc.setTimePath(gc.getTimePath() + 1);
                        pc.setTimeSlot(rootTime);
                    } else {
                        trivial = true;
                        pc.setPrevTimePath(prevPath);
                        pc.setFutTimePath(rootFutPath);
                        pc.setTimePath(rootPath);
                        pc.setTimeSlot(rootTime);
                    }
                } else {
                    trivial = true;
                    pc.setPrevTimePath(gc.getPrevTimePath());
                    pc.setFutTimePath(gc.getFutTimePath());
                    pc.setTimePath(gc.getTimePath());
                    pc.setTimeSlot(gc.getTimeSlot());
                }
                if (rtCardGoals.containsKey(pc.getElId())) {
                    Object[] card = rtCardGoals.get(pc.getElId());
                    Const cardType = (Const) card[0];
                    Integer cardNumber = (Integer) card[1];
                    if (cardType.equals(Const.SEQ))
                        pc.setTimeSlot(pc.getTimeSlot() + cardNumber - 1);
                }
                pc.addFulfillmentConditions(gc.getFulfillmentConditions());
                if (newplan) {
                    addPlan(p, pc, ad);
                    gc.setFutTimePath(Math.max(pc.getTimePath(), pc.getFutTimePath()));
                    if (trivial || (!parPlan && rtAltGoals.get(pc.getElId()) == null))
                        gc.setTimeSlot(pc.getTimeSlot());
                }

            }
//            List<FHardGoal> megoallist = tn.getMeansEndMeanGoals(g); // TODO achar uma situacao em que isso se aplica. Sempre esta sendo vazio
//            for (FHardGoal go : megoallist) {
//                boolean newgoal = !ad.containsGoal(go);
//                GoalContainer pc = ad.createGoal(go, Const.ACHIEVE);
//                gc.addDecomp(pc);
//                if (newgoal)
//                    addGoal(go, pc, ad, true);
//            }
        }
    }

    private void iterateRts(RTContainer gc, List<? extends RTContainer> rts) {
        for (RTContainer dec : rts) {
            String elId = dec.getElId();
            LinkedList<RTContainer> decPlans = RTContainer.fowardMeansEnd(dec, new LinkedList<>());
            if (rtAltGoals.get(elId) != null) {
                // TODO Nunca entra aqui!
                if (!dec.getFirstAlternatives().contains(rts.get(0))) {
                    for (String altGoalId : rtAltGoals.get(elId)) {
                        RTContainer altDec = gc.getDecompElement(altGoalId);
                        if (altDec != null) {
                            LinkedList<RTContainer> decAltPlans = RTContainer.fowardMeansEnd(altDec, new LinkedList<>());
                            if (!decPlans.contains(dec)) {
                                if (dec.getAlternatives().get(dec) == null)
                                    dec.getAlternatives().put(dec, new LinkedList<>());
                                dec.getAlternatives().get(dec).add(altDec);
                            }
                            if (!decAltPlans.contains(altDec))
                                altDec.getFirstAlternatives().add(dec);
                            for (RTContainer decPlan : decPlans) {
                                if (decPlan.getAlternatives().get(dec) == null)
                                    decPlan.getAlternatives().put(dec, new LinkedList<>());
                                decPlan.getAlternatives().get(dec).add(altDec);
                            }
                            for (RTContainer decAltPlan : decAltPlans)
                                decAltPlan.getFirstAlternatives().add(dec);
                        }
                    }
                }
            }
            if (rtTryGoals.get(elId) != null) {
                String[] tryGoals = rtTryGoals.get(elId);
                if (tryGoals[0] != null) {
                    RTContainer successPlan = gc.getDecompElement(tryGoals[0]);
                    LinkedList<RTContainer> decSucessPlans = RTContainer.fowardMeansEnd(successPlan, new LinkedList<>());
                    for (RTContainer decPlan : decPlans) {
                        decPlan.setTrySuccess(successPlan);
                    }
                    for (RTContainer decSucessPlan : decSucessPlans) {
                        decSucessPlan.setTryOriginal(dec);
                        decSucessPlan.setSuccessTry(true);
                    }
                }
                if (tryGoals[1] != null) {
                    RTContainer failurePlan = gc.getDecompElement(tryGoals[1]);
                    LinkedList<RTContainer> decFailurePlans = RTContainer.fowardMeansEnd(failurePlan, new LinkedList<>());
                    for (RTContainer decPlan : decPlans) {
                        decPlan.setTryFailure(failurePlan);
                    }
                    for (RTContainer decFailurePlan : decFailurePlans) {
                        decFailurePlan.setTryOriginal(dec);
                        decFailurePlan.setSuccessTry(false);
                    }
                }
            }
            if (rtOptGoals.containsKey(elId))
                for (RTContainer decPlan : decPlans)
                    decPlan.setOptional(rtOptGoals.get(elId));
            if (rtCardGoals.containsKey(elId)) {
                Object[] card = rtCardGoals.get(elId);
                Const cardType = (Const) card[0];
                Integer cardNumber = (Integer) card[1];
                for (RTContainer decPlan : decPlans) {
                    decPlan.setCardType(cardType);
                    decPlan.setCardNumber(cardNumber);
                }
            }


        }
    }

    private void storeRegexResults(String uid, String rtRegex, Const decType) throws IOException {
        if (rtRegex != null) {
            Object[] res = RTParser.parseRegex(uid, rtRegex + '\n', decType);
            rtSortedGoals.putAll((Map<String, Boolean[]>) res[0]);
            rtCardGoals.putAll((Map<String, Object[]>) res[1]);
            rtAltGoals.putAll((Map<String, Set<String>>) res[2]);
            rtTryGoals.putAll((Map<String, String[]>) res[3]);
            rtOptGoals.putAll((Map<String, Boolean>) res[4]);
        }
    }

    private void sortIntentionalElements(List<? extends GeneralEntity> elements) {
        elements.sort((Comparator<GeneralEntity>) (gA, gB) -> {
            float idA = Float.parseFloat(AgentDefinition.parseElId(gA.getName()).replaceAll("[TG]", ""));
            float idB = Float.parseFloat(AgentDefinition.parseElId(gB.getName()).replaceAll("[TG]", ""));
            return (int) (idA * 1000 - idB * 1000);
        });
    }
}
