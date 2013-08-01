package edu.stanford.nlp.sequences;

import java.io.PrintWriter;
import java.io.Reader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.FullTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.GoldAnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.MorphologyFeatureStringAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.OriginalAnswerAnnotation;
import edu.stanford.nlp.objectbank.DelimitRegExIterator;
import edu.stanford.nlp.objectbank.IteratorFromReaderFactory;
import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.util.StringUtils;


public class LVCoNLLDocumentReaderAndWriter implements DocumentReaderAndWriter<CoreLabel> {

  private static final long serialVersionUID = 3806263423697973704L;

  private IteratorFromReaderFactory<List<CoreLabel>> factory;
  
  public static final String BOUNDARY = "<s>";
  public static final String OTHER = "O";
  private SeqClassifierFlags flags;

  private static final String eol = System.getProperty("line.separator");

  
  public void init(SeqClassifierFlags flags) {
	  this.flags = flags;
	  factory = DelimitRegExIterator.getFactory("\n(?:\\s*\n)+", new ConlllDocParser());
  }


  public Iterator<List<CoreLabel>> getIterator(Reader r) {
    return factory.getIterator(r);
  }

  private int num; // = 0;


  private class ConlllDocParser implements Serializable, Function<String,List<CoreLabel>> {

    private static final long serialVersionUID = -6266332661459630572L;
    private final Pattern whitePattern = Pattern.compile("\\s+");

    int lineCount = 0;

    public List<CoreLabel> apply(String doc) {
      if (num > 0 && num % 1000 == 0) { System.err.print("["+num+"]"); }
      num++;

      List<CoreLabel> words = new ArrayList<CoreLabel>();

      String[] lines = doc.split("\n");

      for (String line : lines) {
        ++lineCount;
//        if (line.trim().length() == 0) {
//          continue;
//        }
        //String[] info = whitePattern.split(line);
        // todo: We could speed things up here by having one time only having converted map into an array of CoreLabel keys (Class<? extends CoreAnnotation<?>>) and then instantiating them. Need new constructor.
        CoreLabel wi;
        try {
          wi = makeCoreLabel(line);
        } catch (RuntimeException e) {
          System.err.println("Error on line " + lineCount + ": " + line);
          throw e;
        }
        words.add(wi);
      }
      return words;
    }
  }


  
//  private List<CoreLabel> processDocument(String doc) {
//	    List<CoreLabel> lis = new ArrayList<CoreLabel>();
//	    String[] lines = doc.split(eol);
//	    for (String line : lines) {
//	      if ( !flags.deleteBlankLines || ! white.matcher(line).matches()) {
//	        lis.add(makeCoreLabel(line));
//	      }
//	    }
//	    return lis;
//	  }


	  /** 
	   *
	   *  @param line A line of CoNLL input
	   *  @return The constructed token
	   */
	  private CoreLabel makeCoreLabel(String line) {
	    CoreLabel wi = new CoreLabel();
	    // wi.line = line;
	    String[] bits = line.split("\\s+");
	    if (bits.length <= 1) {
	    	wi.setWord(BOUNDARY);
	        wi.set(AnswerAnnotation.class, OTHER);
	    } else if (bits.length >= 6) {
	    	//conll-x format produced by morphotagger
	    	wi.setIndex(Integer.parseInt(bits[0]));
	    	wi.setWord(bits[1]);
	    	wi.setLemma(bits[2]);
	    	wi.set(FullTagAnnotation.class, bits[4]);
	    	wi.setTag(bits[4].substring(0,1));
	    	wi.set(MorphologyFeatureStringAnnotation.class, bits[5]);
	    } else {
	    	throw new RuntimeIOException("Unexpected input (many fields): " + line);
	    }

	    wi.set(OriginalAnswerAnnotation.class, wi.get(AnswerAnnotation.class));
	    return wi;
	  }

	  private String intern(String s) {
	    if (flags.intern) {
	      return s.intern();
	    } else {
	      return s;
	    }
	  }


	  /** Write a CoNLL format output file.
	   *
	   *  @param doc The document: A List of CoreLabel
	   *  @param out Where to send the answers to
	   */
	  @SuppressWarnings({"StringEquality"})
	  public void printAnswers(List<CoreLabel> doc, PrintWriter out) {
		for (CoreLabel fl : doc) {
	      String word = fl.word();
	      if (word == BOUNDARY) {
	        out.println();
	      } else {
	        String gold = fl.get(OriginalAnswerAnnotation.class);
	        if(gold == null) gold = "";
	        String guess = fl.get(AnswerAnnotation.class);
	        
	        String tag = fl.tag();
	        String lemma = fl.lemma();
	        String fullTag = fl.getString(FullTagAnnotation.class);
	        String morphoFeats = fl.getString(MorphologyFeatureStringAnnotation.class);
	        
	        out.println(fl.index() + "\t" + word + '\t' + lemma + '\t' + tag + '\t' + 
	        		fullTag + '\t' + morphoFeats + '\t' + guess);
	      }
	    }
	  }
	  

//  public void printAnswers(List<CoreLabel> doc, PrintWriter out) {
//	    for (CoreLabel wi : doc) {
//	      String answer = wi.get(AnswerAnnotation.class);
//	      String goldAnswer = wi.get(GoldAnswerAnnotation.class);
//	      out.println(wi.word() + "\t" + goldAnswer + "\t" + answer);
//	    }
//	    out.println();
//  }

}
