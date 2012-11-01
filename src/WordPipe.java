import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.json.simple.JSONValue;

import lv.semti.morphology.analyzer.MarkupConverter;
import lv.semti.morphology.analyzer.Word;
import lv.semti.morphology.analyzer.Wordform;
import lv.semti.morphology.attributes.AttributeNames;
import lv.semti.morphology.attributes.AttributeValues;

import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ie.ner.CMMClassifier;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.LVMorphologyAnalysis;
import edu.stanford.nlp.ling.CoreAnnotations.LVMorphologyAnalysisBest;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.sequences.LVMorphologyReaderAndWriter;

// Copied/pasted/mangled from transliteration webservices java project

public class WordPipe {
	public static void main(String[] args) throws Exception {
		String field_separator = "\t";
		String token_separator = "\n";
		boolean json = true;
		boolean mini_tag = false;
		boolean vert_input = false;
		for (int i=0; i<args.length; i++) {
			if (args[i].equalsIgnoreCase("-tab")) {  // one response line per each query line, tab-separated
				json = false;
				token_separator = "\t";
			}
			if (args[i].equalsIgnoreCase("-vert")) { // one response line per token, tab-separated
				json = false;
			}
			if (args[i].equalsIgnoreCase("-moses")) { // one response line per token, pipe-separated
				field_separator = "|";
				token_separator = " ";
				json = false;
			}
			if (args[i].equalsIgnoreCase("-stripped")) mini_tag = true; //remove nonlexical attributes
			if (args[i].equalsIgnoreCase("-vertinput")) vert_input = true; //vertical input format as requested by Milos Jakubicek 2012.11.01
			
			if (args[i].equalsIgnoreCase("-h") || args[i].equalsIgnoreCase("--help") || args[i].equalsIgnoreCase("-?")) {
				System.out.println("LV morphological tagger");
				System.out.println("\nInput formats");
				System.out.println("\tDefault : plain text UTF-8, one sentence per line.");
				System.out.println("\t-vertinput : one line per token, sentences separated by <s></s>. Any XML-style tags are echoed as-is. \n\t\tNB! sentences are retokenized, the number of tokens may be different.");
				System.out.println("\nOutput formats");
				System.out.println("\tDefault : JSON. Each sentence is returned as a list of dicts, each dict contains elements 'Word', 'Tag' and 'Lemma'.");
				System.out.println("\t-tab : one response line for each query line; tab-separated lists of word, tag and lemma.");
				System.out.println("\t-vert : one response line for each token; tab-separated lists of word, tag and lemma.");
				System.out.println("\t-moses : one response line for each token; pipe-separated lists of word, tag and lemma.");
				System.out.println("\nOther options:");
				System.out.println("\t-stripped : lexical/nonessential parts of the tag are replaced with '-' to reduce sparsity.");
				System.out.flush();
				System.exit(0);
			}
		}
				
		String serializedClassifier = "MorfoCRF/lv-morpho-model.ser.gz"; //FIXME - make it configurable
		AbstractSequenceClassifier<CoreLabel> cmm = CMMClassifier.getClassifier(serializedClassifier);
			
		PrintStream out = new PrintStream(System.out, true, "UTF8");
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in, "UTF8"));
		
	    String s;
	    String sentence = "";
	    while ((s = in.readLine()) != null && s.length() != 0) {
	    	boolean finished = true; // is sentence finished and ready to analyze
	    	if (!vert_input) sentence = s;
	    	else {
	    		if (s.startsWith("<") && s.length()>1) out.println(s);
	    		else sentence = sentence + " " + s;
	    		finished = s.startsWith("</s>");
	    	}	    	
	    	if (finished) {
		    	List<CoreLabel> doc = LVMorphologyReaderAndWriter.analyzeSentence(sentence.trim());
		    	cmm.classify(doc); // runs the actual morphotagging system
		    	if (json)  // output format choice
		    		out.println( analyze( doc));
		    	else out.println( analyze_separated(doc, field_separator, token_separator, mini_tag));
		    	out.flush();
		    	sentence = "";
	    	}
	    }
    	if (vert_input && sentence.length()>0) { //FIXME, not DRY
	    	List<CoreLabel> doc = LVMorphologyReaderAndWriter.analyzeSentence(sentence);
	    	cmm.classify(doc); // runs the actual morphotagging system
	    	if (json)  // output format choice
	    		out.println( analyze( doc));
	    	else out.println( analyze_separated(doc, field_separator, token_separator, mini_tag));
	    	out.flush();
    	}	    
	}	
	
	private static String analyze(List<CoreLabel> tokens) {		
		LinkedList<String> tokenJSON = new LinkedList<String>();
		
		for (CoreLabel word : tokens) {
			String token = word.getString(TextAnnotation.class);
			if (token.contains("<s>")) continue;
			Word analysis = word.get(LVMorphologyAnalysis.class);
			Wordform maxwf = chooseWordform(analysis, word.getString(AnswerAnnotation.class)); 
			if (maxwf != null)
				tokenJSON.add(String.format("{\"Word\":\"%s\",\"Tag\":\"%s\",\"Lemma\":\"%s\"}", JSONValue.escape(token), JSONValue.escape(maxwf.getTag()), JSONValue.escape(maxwf.getValue(AttributeNames.i_Lemma))));
			else 
				tokenJSON.add(String.format("{\"Word\":\"%s\",\"Tag\":\"-\",\"Lemma\":\"%s\"}", JSONValue.escape(token), JSONValue.escape(token)));			
		}
		
		String s = formatJSON(tokenJSON).toString();
		tokens = null;
		tokenJSON = null;
		
		return s;
	}
	
	private static String analyze_separated(List<CoreLabel> tokens, String field_separator, String token_separator, boolean mini_tag){
		StringBuilder s = new StringBuilder();
		
		for (CoreLabel word : tokens) {
			String token = word.getString(TextAnnotation.class);
			if (token.contains("<s>")) continue;
			if (s.length()>0) s.append(token_separator);
			s.append(token);
			s.append(field_separator);
			/*
			s.append(word.getString(AnswerAnnotation.class));
			s.append("\t");
			*/
			Word analysis = word.get(LVMorphologyAnalysis.class);
			Wordform mainwf = chooseWordform(analysis, word.getString(AnswerAnnotation.class)); 
			if (mainwf != null) {
				if (mini_tag) mainwf.removeNonlexicalAttributes();
				s.append(mainwf.getTag());
				s.append(field_separator);
				s.append(mainwf.getValue(AttributeNames.i_Lemma));
				//s.append("\t");
			} else s.append(field_separator); 
			/*
			mainwf = word.get(LVMorphologyAnalysisBest.class);
			if (mainwf != null) {
				s.append("Statistics:\t");
				s.append(mainwf.getTag());
				s.append("\t");
				s.append(mainwf.getValue(AttributeNames.i_Lemma));
				s.append("\t");
			}
			s.append("\n");*/
			//if (all_options)
		//		s.append(word.toTabSep(statistics, probabilities));
			//else s.append(word.toTabSepsingle(statistics));
		}
		
		tokens = null;
		return s.toString();
	}
	
	private static StringBuilder formatJSON(Collection<String> tags) {
		Iterator<String> i = tags.iterator();
		StringBuilder out = new StringBuilder("[");
		while (i.hasNext()) {
			out.append(i.next());
			if (i.hasNext()) out.append(", ");
		}
		out.append("]");
		return out;
	}
	
	
	private static Wordform chooseWordform(Word analysis, String answerTag) {
		Wordform result = null;
		AttributeValues av = MarkupConverter.fromKamolsMarkup(answerTag);
		for (Wordform wf : analysis.wordforms) {
			if (wf.isMatchingWeak(av)) {
				if (result != null) 
					System.err.printf("Multiple valid options for word %s tag %s: %s and %s\n", analysis.getToken(), answerTag, wf.getTag(), result.getTag());
				result = wf;
			}
		}
		
		if (result == null) {
			result = new Wordform(analysis.getToken());
			result.addAttributes(av);
			result.addAttribute(AttributeNames.i_Source, "CMM tagger guess");
			result.addAttribute(AttributeNames.i_Lemma, analysis.getToken());
			System.err.printf("None of analysis options valid for word %s tag %s\n", analysis.getToken(), answerTag);
		}
		
		return result;
	}
}	