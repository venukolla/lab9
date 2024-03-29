package com.ibm.cloudoe.referance;
/*
 * NewsTerp Engine - We report.  You decipher.
 * copyright (c) 2007 Colin Bayer, Jack Hebert
 *
 * CSE 472 Spring 2007 final project
 */

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

/*import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;*/

public class Main extends HttpServlet {
	public static void usage() {
		System.err.println(
			"Usage: java Main [-nlp <dir>] [-wn <dir>] [-nnp <n>] <file1>\n" +
				"\t\t<file2> ... <fileN>\n" +
			"\t-p1:\t use phase 1 extractor\n" +
			"\t-p2:\t use phase 2 extractor (default)\n" +
			"\t-nlp <dir>:\t OpenNLP Tools root directory\n" +
			"\t-wn <dir>:\t WordNet dictionary location (absolute path)\n" +
			"\t-nnp <n>: Number of most-popular NPs to print\n"
		);
	}

	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
		doPost(request, response);
	}
	
	
	


	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		ClassLoader classLoader = this.getClass().getClassLoader();

		String outls=" ";
		if(ServletFileUpload.isMultipartContent(request)){
            try {
                List<FileItem> multiparts = new ServletFileUpload( new DiskFileItemFactory()).parseRequest( request);
                for(FileItem item : multiparts){
                    if(!item.isFormField()){
                    	System.out.println(classLoader.getResource("test"));
                        item.write( new File(classLoader.getResource("test").getFile()));
                    }
                }
            
               request.setAttribute("message", "File Uploaded Successfully");
            } catch (Exception ex) {
               request.setAttribute("message", "Failed due to " + ex);
               System.out.println(ex.getMessage());
            }         
          
        }else{
            request.setAttribute("message",
                                 "Servlet only handles file upload request");
        }

		int idx = 0;
		//System.out.println(getServletContext().getResource("/"));
		String nlp_path =classLoader.getResource("opennlp-tools-1.3.0").getFile();
		URL wn_path = null;
		int numToShow = 350;
		try {
			wn_path = classLoader.getResource("dict");
			System.out.println(wn_path);
			
		} catch (Exception e) {}
		int extr_phase = 2;

		if (!NLPToolkitManager.init(
				nlp_path + "/models/english/sentdetect/EnglishSD.bin.gz",
				nlp_path + "/models/english/tokenize/EnglishTok.bin.gz",
				nlp_path + "/models/english/parser/tag.bin.gz",
				nlp_path + "/models/english/parser/tagdict",
				nlp_path + "/models/english/chunker/EnglishChunk.bin.gz",
				wn_path)) {
			System.err.println("Error creating NLP objects, exiting...");
			return;		
		}

		
		ArrayList<TaggedArticle> articleList = new ArrayList<TaggedArticle>();

		
			try {
			    NewsRepoReader reader = new NewsRepoReader(classLoader.getResource("test").getFile());
			    NewsRepoArticle article = reader.GetNextArticle();
			    int count = 0;
			    while(article != null) {
			    	count += 1;
			    	System.out.println("*****************************");
			    	System.out.println(count+"/"+reader.GetNumberOfArticle());
			    	articleList.add(new TaggedArticle(article.getUrl(), article.getArticle()));
			    	article = reader.GetNextArticle();
			    }
			    //articles[n] = new TaggedArticle(aArgs[idx], aArgs[idx]);
			} catch (IOException e) {}

		
		HashMap<TaggedSentence.Chunk, Integer> np_pop_index = 
			new HashMap<TaggedSentence.Chunk, Integer>();
		HashMap<TaggedSentence.Chunk, Integer> vp_pop_index = 
			new HashMap<TaggedSentence.Chunk, Integer>();
		RelationExtractor re;

		if (extr_phase == 1) {
			re = new Phase1RelationExtractor();
		} else {
			re = new Phase2RelationExtractor();
		}

		int a_i = 0, s_i = 0;

		ArrayList<RelationSet> rel_sets = new ArrayList<RelationSet>();

		for (TaggedArticle a : articleList) {
			s_i = 0;

			RelationSet set = new RelationSet(a.getID());

			for (TaggedSentence s : a.getSentences()) {
				Relation[] r = null;

				if ((r = re.extract(s)) != null && r.length != 0) {
					System.out.println("Extracted relations for article " + a_i + 
						", sentence " + s_i + ": " + Arrays.toString(r));
					for (Relation rel : r) {
						rel.annotate(new SentenceNoAnnotation(s_i));
						rel.annotate(new HumanReadableSentenceAnnotation(
							s.humanReadableSentence()));
						set.add(rel);
					}
				}

			

				for (TaggedSentence.Chunk ck : s.getChunks()) {
					Integer ck_ct = null;

					if (ck.getType() == ChunkType.NP) {
						
						TaggedWord[] w = ck.getWords();

						if (w.length == 1 &&
							(w[0].getPOS() == PartOfSpeech.PERS_PN) ||
							(w[0].getPOS() == PartOfSpeech.WH_DET) ||
							(w[0].getPOS() == PartOfSpeech.WH_PN)) continue;

						if ((ck_ct = np_pop_index.get(ck)) != null) {
							np_pop_index.put(ck, 
								new Integer(ck_ct.intValue() + 1));
						} else {
							np_pop_index.put(ck, new Integer(1));
						}
					} else if (ck.getType() == ChunkType.VP) {
						if ((ck_ct = vp_pop_index.get(ck)) != null) {
							vp_pop_index.put(ck,
								new Integer(ck_ct.intValue() + 1));
						} else {
							vp_pop_index.put(ck, new Integer(1));
						}
					}
				}

				s_i++;
			}

			rel_sets.add(set);

			a_i++;
		}

		// don't ask why Java doesn't let you make genericized arrays. just
		// accept that this line works, and move on.
		Map.Entry<TaggedSentence.Chunk, Integer>[] np_pop_entries =
			(Map.Entry<TaggedSentence.Chunk, Integer>[]) new Map.Entry[0];

		np_pop_entries = np_pop_index.entrySet().toArray(np_pop_entries);

		Arrays.sort(np_pop_entries, 
			new Comparator< Map.Entry<TaggedSentence.Chunk, Integer> > () {
				public int 
					compare(Map.Entry<TaggedSentence.Chunk, Integer> aA,
							Map.Entry<TaggedSentence.Chunk, Integer> aB) {
					return -aA.getValue().compareTo(aB.getValue());
				}
			}
		);

		Map.Entry<TaggedSentence.Chunk, Integer>[] vp_pop_entries =
			(Map.Entry<TaggedSentence.Chunk, Integer>[]) new Map.Entry[0];

		vp_pop_entries = vp_pop_index.entrySet().toArray(vp_pop_entries);

		Arrays.sort(vp_pop_entries, 
			new Comparator< Map.Entry<TaggedSentence.Chunk, Integer> > () {
				public int 
					compare(Map.Entry<TaggedSentence.Chunk, Integer> aA,
							Map.Entry<TaggedSentence.Chunk, Integer> aB) {
					return -aA.getValue().compareTo(aB.getValue());
				}
			}
		);

		System.out.println("Most popular " + numToShow + 
			" NPs in article set:");
		outls +="<br>"+"Most popular " + numToShow + 
				" NPs in article set:";
		numToShow=np_pop_entries.length;
		for (int i = 0; i < numToShow; i++) {
			System.out.println(np_pop_entries[i].getKey() + " (" + 
				np_pop_entries[i].getValue() + ")");
			outls +="<br>"+np_pop_entries[i].getKey() + " (" + 
					np_pop_entries[i].getValue() + ")";
		}

		System.out.println("Most popular " + numToShow + 
			" VPs in article set:");
		outls +="<br>"+"Most popular " + numToShow +	" VPs in article set:";
		numToShow=vp_pop_entries.length;
		for (int i = 0; i < numToShow; i++) {
			outls +="<br>"+vp_pop_entries[i].getKey() + " (" +
					vp_pop_entries[i].getValue() + ")";
			System.out.println(vp_pop_entries[i].getKey() + " (" +
				vp_pop_entries[i].getValue() + ")");
		}

		// dump relation sets to a file.
		
		request.setAttribute("OutPut",outls);
		request.getRequestDispatcher("start.jsp").forward(request, response); 
		
	}

	public static void main(String[] aArgs) {
		int idx = 0;
		//String nlp_path = "E:\\Users\\shenfc\\workspace\\text_Runner\\opennlp-tools-1.3.0";
		String nlp_path = "F:\\Masters\\KDM\\Tutorials\\CS560_T9\\text_Runner\\opennlp-tools-1.3.0";
		URL wn_path = null;
		int numToShow = 350;
		try {
			//wn_path = new URL("file:///E:\\Program Files\\WordNet\\2.1\\dict");
			
			wn_path = new URL("file:///C:\\Program Files (x86)\\WordNet\\3.0\\dict");
			/*String pwd = System.getProperty("user.dir");

			if (pwd != null) {
				wn_path = new File(pwd).toURL();
			}*/
		} catch (Exception e) {}
		int extr_phase = 2;

	/*	while (idx < aArgs.length) {
			if (aArgs[idx].equals("-nlp")) {
				nlp_path = aArgs[idx + 1];
				idx += 2;
				continue;
			} else if (aArgs[idx].equals("-wn")) {
				try {
					wn_path = new URL(aArgs[idx + 1]);
				} catch (Exception e) {
					System.err.println("Malformed WordNet path URL (" + e + "); exiting...");
					return;
				}
				idx += 2;
				continue;
			} else if (aArgs[idx].equals("-nnp")) {
				numToShow = Integer.parseInt(aArgs[idx+1]);
				idx += 2;
				continue;
			} else if (aArgs[idx].equals("-p1")) {
				extr_phase = 1;
				idx++;
				continue;
			} else if (aArgs[idx].equals("-p2")) {
				extr_phase = 2;
				idx++;
				continue;
			} else if (aArgs[idx].equals("-h")) {
				usage();
				return;
			} else {
				 idx now points to the first file in the arg list 
				break;
			}*/
	//	}

		if (aArgs.length - idx <= 0) {
			/* we need at least one file to extract main ideas from. */
			usage();
			return;
		}

		/* build OpenNLP processing objects */
		if (!NLPToolkitManager.init(
				nlp_path + "/models/english/sentdetect/EnglishSD.bin.gz",
				nlp_path + "/models/english/tokenize/EnglishTok.bin.gz",
				nlp_path + "/models/english/parser/tag.bin.gz",
				nlp_path + "/models/english/parser/tagdict",
				nlp_path + "/models/english/chunker/EnglishChunk.bin.gz",
				wn_path)) {
			System.err.println("Error creating NLP objects, exiting...");
			return;		
		}

		/* provide space to store the processed articles... */
		//TaggedArticle[] articles = new TaggedArticle[aArgs.length - idx];
		ArrayList<TaggedArticle> articleList = new ArrayList<TaggedArticle>();

		/* chop up and tag all of our articles. */
		for (int n = 0; idx < aArgs.length; idx++, n++) {
			try {
			    NewsRepoReader reader = new NewsRepoReader(aArgs[idx]);
			    NewsRepoArticle article = reader.GetNextArticle();
			    int count = 0;
			    while(article != null) {
			    	count += 1;
			    	System.out.println("*****************************");
			    	System.out.println(count+"/"+reader.GetNumberOfArticle());
			    	articleList.add(new TaggedArticle(article.getUrl(), article.getArticle()));
			    	article = reader.GetNextArticle();
			    }
			    //articles[n] = new TaggedArticle(aArgs[idx], aArgs[idx]);
			} catch (IOException e) {}
		}

		// TODO: convert arrayList back to array?

		// do per-article-set fancy stuff here.
		HashMap<TaggedSentence.Chunk, Integer> np_pop_index = 
			new HashMap<TaggedSentence.Chunk, Integer>();
		HashMap<TaggedSentence.Chunk, Integer> vp_pop_index = 
			new HashMap<TaggedSentence.Chunk, Integer>();
		RelationExtractor re;

		if (extr_phase == 1) {
			re = new Phase1RelationExtractor();
		} else {
			re = new Phase2RelationExtractor();
		}

		int a_i = 0, s_i = 0;

		ArrayList<RelationSet> rel_sets = new ArrayList<RelationSet>();

		for (TaggedArticle a : articleList) {
			s_i = 0;

			RelationSet set = new RelationSet(a.getID());

			for (TaggedSentence s : a.getSentences()) {
				Relation[] r = null;

				if ((r = re.extract(s)) != null && r.length != 0) {
					/*System.out.println("Extracted relations for article " + a_i + 
						", sentence " + s_i + ": " + Arrays.toString(r));*/
					for (Relation rel : r) {
						rel.annotate(new SentenceNoAnnotation(s_i));
						rel.annotate(new HumanReadableSentenceAnnotation(
							s.humanReadableSentence()));
						set.add(rel);
					}
				}

				/*if (s.getChunks(ChunkType.SBAR).length != 0) {
					System.out.println(a_i + ":" + s_i + " (" + s + 
						"; " + Arrays.toString(s.getChunks()) + 
						") has an SBAR.");
				}*/

				for (TaggedSentence.Chunk ck : s.getChunks()) {
					Integer ck_ct = null;

					if (ck.getType() == ChunkType.NP) {
						/* skip wh-determiners (which, that), wh-pronouns
						   (what, who), and personal pronouns (it, he, she) */
						TaggedWord[] w = ck.getWords();

						if (w.length == 1 &&
							(w[0].getPOS() == PartOfSpeech.PERS_PN) ||
							(w[0].getPOS() == PartOfSpeech.WH_DET) ||
							(w[0].getPOS() == PartOfSpeech.WH_PN)) continue;

						if ((ck_ct = np_pop_index.get(ck)) != null) {
							np_pop_index.put(ck, 
								new Integer(ck_ct.intValue() + 1));
						} else {
							np_pop_index.put(ck, new Integer(1));
						}
					} else if (ck.getType() == ChunkType.VP) {
						if ((ck_ct = vp_pop_index.get(ck)) != null) {
							vp_pop_index.put(ck,
								new Integer(ck_ct.intValue() + 1));
						} else {
							vp_pop_index.put(ck, new Integer(1));
						}
					}
				}

				s_i++;
			}

			rel_sets.add(set);

			a_i++;
		}

		// don't ask why Java doesn't let you make genericized arrays. just
		// accept that this line works, and move on.
		Map.Entry<TaggedSentence.Chunk, Integer>[] np_pop_entries =
			(Map.Entry<TaggedSentence.Chunk, Integer>[]) new Map.Entry[0];

		np_pop_entries = np_pop_index.entrySet().toArray(np_pop_entries);

		Arrays.sort(np_pop_entries, 
			new Comparator< Map.Entry<TaggedSentence.Chunk, Integer> > () {
				public int 
					compare(Map.Entry<TaggedSentence.Chunk, Integer> aA,
							Map.Entry<TaggedSentence.Chunk, Integer> aB) {
					return -aA.getValue().compareTo(aB.getValue());
				}
			}
		);

		Map.Entry<TaggedSentence.Chunk, Integer>[] vp_pop_entries =
			(Map.Entry<TaggedSentence.Chunk, Integer>[]) new Map.Entry[0];

		vp_pop_entries = vp_pop_index.entrySet().toArray(vp_pop_entries);

		Arrays.sort(vp_pop_entries, 
			new Comparator< Map.Entry<TaggedSentence.Chunk, Integer> > () {
				public int 
					compare(Map.Entry<TaggedSentence.Chunk, Integer> aA,
							Map.Entry<TaggedSentence.Chunk, Integer> aB) {
					return -aA.getValue().compareTo(aB.getValue());
				}
			}
		);

		System.out.println("Most popular " + numToShow + 
			" NPs in article set:");
		numToShow=np_pop_entries.length;
		for (int i = 0; i < numToShow; i++) {
			System.out.println(np_pop_entries[i].getKey() + " (" + 
				np_pop_entries[i].getValue() + ")");
		}

		System.out.println("Most popular " + numToShow + 
			" VPs in article set:");
		numToShow=vp_pop_entries.length;
		for (int i = 0; i < numToShow; i++) {
			System.out.println(vp_pop_entries[i].getKey() + " (" +
				vp_pop_entries[i].getValue() + ")");
		}

		// dump relation sets to a file.
		try {
			FileWriter rsf = new FileWriter("relations-phase" + extr_phase + 
				".dat");

			for (RelationSet rs : rel_sets) {
				rsf.write(rs.toSerialRep());
			}

			rsf.write("\n");

			rsf.close();
		} catch (IOException e) {
			System.err.println("Warning: couldn't dump relation sets...");
		}
	}
}
