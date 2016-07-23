/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gate.plugin.tagger.googlenlp;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.language.v1beta1.CloudNaturalLanguageAPI;
import com.google.api.services.language.v1beta1.CloudNaturalLanguageAPI.Documents.AnnotateText;
import com.google.api.services.language.v1beta1.CloudNaturalLanguageAPIScopes;
import com.google.api.services.language.v1beta1.model.AnnotateTextRequest;
import com.google.api.services.language.v1beta1.model.AnnotateTextResponse;
import com.google.api.services.language.v1beta1.model.DependencyEdge;
import com.google.api.services.language.v1beta1.model.Entity;
import com.google.api.services.language.v1beta1.model.EntityMention;
import com.google.api.services.language.v1beta1.model.Features;
import com.google.api.services.language.v1beta1.model.Sentence;
import com.google.api.services.language.v1beta1.model.Sentiment;
import com.google.api.services.language.v1beta1.model.TextSpan;
import com.google.api.services.language.v1beta1.model.Token;
import gate.Annotation;
import gate.AnnotationSet;
import gate.Controller;
import gate.Document;
import gate.FeatureMap;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;
import gate.util.GateRuntimeException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;

/**
 *
 * @author Johann Petrak
 */
@CreoleResource(name = "Tagger_GoogleNLP",
        comment = "Annotate documents using a Google NLP web service",
        // icon="taggerIcon.gif",
        helpURL = "https://github.com/SheffieldGATE/gateplugin-Tagger_TagMe/wiki/Tagger_TagMe"
)
public class TaggerGateNLP extends AbstractDocumentProcessor {

  // PR PARAMETERS
  protected String containingAnnotationType = "";

  @CreoleParameter(comment = "The annotation that covers the document text to annotate", defaultValue = "")
  @RunTime
  @Optional
  public void setContainingAnnotationType(String val) {
    containingAnnotationType = val;
  }

  public String getContainingAnnotationType() {
    return containingAnnotationType;
  }

  protected String inputAnnotationSet = "";
  @CreoleParameter(comment = "The input annotation set", defaultValue = "")
  @RunTime
  @Optional
  public void setInputAnnotationSet(String val) {
    inputAnnotationSet = val;
  }

  public String getInputAnnotationSet() {
    return inputAnnotationSet;
  }
  
  protected String outputAnnotationSet = "";
  @CreoleParameter(comment = "The output annotation set", defaultValue = "")
  @RunTime
  @Optional
  public void setOutputAnnotationSet(String val) {
    outputAnnotationSet = val;
  }

  public String getOutputAnnotationSet() {
    return outputAnnotationSet;
  }
  
  protected Boolean annotateSyntax = false;
  @CreoleParameter(comment = "If syntax information should get annotated", defaultValue = "false")
  @RunTime
  @Optional
  public void setAnnotateSyntax(Boolean val) {
    annotateSyntax = val;
  }

  public Boolean getAnnotateSyntax() {
    return annotateSyntax;
  }
  
  protected Boolean annotateEntities = false;
  @CreoleParameter(comment = "If entities should get annotated", defaultValue = "true")
  @RunTime
  @Optional
  public void setAnnotateEntities(Boolean val) {
    annotateEntities = val;
  }

  public Boolean getAnnotateEntities() {
    return annotateEntities;
  }

  protected Boolean annotateSentiment = false;
  @CreoleParameter(comment = "If sentiment should get annotated", defaultValue = "true")
  @RunTime
  @Optional
  public void setAnnotateSentiment(Boolean val) {
    annotateSentiment = val;
  }

  public Boolean getAnnotateSentiment() {
    return annotateSentiment;
  }


  
  
  protected URL keyFileUrl;

  @CreoleParameter(comment = "The URL of the JSON key file that contains the private key",
          defaultValue = "", suffixes = ".json")
  @RunTime
  @Optional
  public void setKeyFileUrl(URL val) {
    keyFileUrl = val;
  }

  public URL getKeyFileUrl() {
    return keyFileUrl;
  }

  protected String applicationName = "";

  @CreoleParameter(comment = "The application name to pass on to the Google service", defaultValue = "GateTaggerGoogleNLP")
  @RunTime
  @Optional
  public void setApplicationName(String val) {
    applicationName = val;
  }

  public String getApplicationName() {
    return applicationName;
  }
  
  
  // TODO: parameter to choose language, string with language code, if empty, autodetect
  

  // Fields
  
  CloudNaturalLanguageAPI serviceApi = null;
  
  // Helper methods
  /**
   * Create a credentials instance. If we have the URL of the JSON key file, use it otherwise check
   * if the environment variable GOOGLE_APPLICATION_CREDENTIALS is set We try to figure out if we
   * got some proper credentials by checking if the credentials instance returns a non-null private
   * key id. If not, we throw an exception.
   */
  public GoogleCredential getCredentials() {
    GoogleCredential cred = null;
    if (keyFileUrl != null) {
      try {
        InputStream is = keyFileUrl.openStream();
        cred = GoogleCredential.fromStream(is).createScoped(CloudNaturalLanguageAPIScopes.all());
      } catch (Exception ex) {
        throw new GateRuntimeException("Could not create credentials from " + keyFileUrl, ex);
      }
    } else {
      try {
        cred = GoogleCredential.getApplicationDefault().createScoped(CloudNaturalLanguageAPIScopes.all());
      } catch (Exception ex) {
        throw new GateRuntimeException("Could not create Google Application Default Credentials", ex);
      }
    }
    if (cred.getServiceAccountPrivateKeyId() == null) {
      throw new GateRuntimeException("Could not establish credentials");
    }
    System.err.println("DEBUG: credentials created: " + cred);
    return cred;
  }

  /**
   * Get an instance of the language service.
   *
   * @param document
   * @return
   */
  public CloudNaturalLanguageAPI getApiService(GoogleCredential credential) {
    final GoogleCredential cred = credential;
    CloudNaturalLanguageAPI api = null;
    try {
      api = new CloudNaturalLanguageAPI.Builder(
              GoogleNetHttpTransport.newTrustedTransport(),
              JacksonFactory.getDefaultInstance(),
              new HttpRequestInitializer() {
        @Override
        public void initialize(HttpRequest httpRequest) throws IOException {
          cred.initialize(httpRequest);
        }
      }).setApplicationName(getApplicationName()).build();
    } catch (Exception ex) {
      throw new GateRuntimeException("Could not establish Google Service API", ex);
    }
    System.err.println("DEBUG: API instance established: " + api);
    return api;
  }

  public AnnotateTextResponse annotateText(
          CloudNaturalLanguageAPI api,
          String text,
          boolean extractSyntax, boolean extractEntities, boolean extractSentiment) {
    Features features = new Features();
    features.setExtractSyntax(extractSyntax);
    features.setExtractEntities(extractEntities);
    features.setExtractDocumentSentiment(extractSentiment);
    AnnotateTextRequest request
            = new AnnotateTextRequest();
    com.google.api.services.language.v1beta1.model.Document doc
            = new com.google.api.services.language.v1beta1.model.Document();
    doc.setContent(text);
    doc.setType("PLAIN_TEXT"); // alternative would be HTML but we do not use this     
    request.setDocument(doc);
    request.setFeatures(features);
    request.setEncodingType("UTF16");
    AnnotateText at;
    try {
      at = api.documents().annotateText(request);
    } catch (IOException ex) {
      throw new GateRuntimeException("Could not create AnnotateText instance", ex);
    }
    System.err.println("DEBUG: Got an AnnotateText instance: " + at);
    AnnotateTextResponse response = null;
    try {
      response = at.execute();
      System.err.println("DEBUG: Got a response: " + response);
    } catch (IOException ex) {
      throw new GateRuntimeException("Could not retrieve the annotate text response from the service", ex);
    }
    return response;
  }

  @Override
  protected Document process(Document document) {
    if(isInterrupted()) {
      interrupted = false;
      throw new GateRuntimeException("Processing has been interrupted");
    }
    if(getContainingAnnotationType() != null && !getContainingAnnotationType().isEmpty()) {
      AnnotationSet anns = document.getAnnotations(getInputAnnotationSet()).get(getContainingAnnotationType());
      for(Annotation ann : anns) {
        String text = gate.Utils.stringFor(document, ann);
        AnnotateTextResponse resp = annotateText(serviceApi,text,
                getAnnotateSyntax(), getAnnotateEntities(), getAnnotateSentiment());
        addOutputAnnotations(document,resp,gate.Utils.start(ann),gate.Utils.end(ann));
      }
    }
    return document;
  }
  
  public void addOutputAnnotations(Document document,AnnotateTextResponse resp,
          long from, long to) {
    AnnotationSet outset = document.getAnnotations(getOutputAnnotationSet());
    Sentiment sentiment = resp.getDocumentSentiment();
    if(sentiment != null) {
      FeatureMap fm = gate.Utils.featureMap("magnitute",sentiment.getMagnitude(),"polarity",sentiment.getPolarity());
      gate.Utils.addAnn(outset, from, to, "Sentiment", fm);
    }
    List<Entity> entities = resp.getEntities();
    if(entities!=null && !entities.isEmpty()) {
      for(Entity entity : entities) {
        List<EntityMention> mentions = entity.getMentions();
        for(EntityMention mention : mentions) {
          TextSpan span = mention.getText();
          FeatureMap fm = gate.Utils.featureMap();
          fm.put("type",entity.getType());
          String wpurl = entity.getMetadata().get("wikipedia_url");          
          fm.put("wikipedia_url",wpurl);
          // TODO: maybe try to add auto-generated dbpedia URI 
          fm.put("salience",entity.getSalience());
          int start=span.getBeginOffset();
          int end=start+span.getContent().length();
          gate.Utils.addAnn(outset, from+start, from+end, "Entity", fm);
        }
      }
    } // if entities 
    // TODO: if we did not specify a language, annotate the language of the text!!
    List<Sentence> sentences = resp.getSentences();
    if(sentences != null && !sentences.isEmpty()) {
      for(Sentence sentence : sentences) {
        TextSpan span = sentence.getText(); 
        int start = span.getBeginOffset();
        int end = span.getContent().length()+start;
        // TODO: annotate GoogleSentences
      }
    } // if sentences
    List<Token> tokens = resp.getTokens();
    if(tokens != null && !tokens.isEmpty()) {
      // TODO: to annotate the tokens with the dependency parser edges, we need to 
      // replace the headTokenIndex (which presumably points to the index of the head token
      // in the List<Token>) with the annotation id of the annotation we create from this. 
      // We do this by going through this list once, creating the annotations and storing the 
      // annotations in a parallel list, then going through this list another time and 
      // creating the edge annotations using the parallel list to get the annotation ids. 
      // TODO: split into two iterations and actually create the annotations.
      for(Token token : tokens) {
        TextSpan span = token.getText();
        int start = span.getBeginOffset();
        int end = span.getContent().length()+start;
        String lemma = token.getLemma();
        String pos = token.getPartOfSpeech().getTag();
        DependencyEdge edge = token.getDependencyEdge();
        int headTokenIndex = edge.getHeadTokenIndex();
        String label = edge.getLabel();
      }
    } // if tokens
  }

  @Override
  protected void beforeFirstDocument(Controller ctrl) {
    GoogleCredential cred = getCredentials();
    serviceApi = getApiService(cred);
  }

  @Override
  protected void afterLastDocument(Controller ctrl, Throwable t) {
    // nothing to do
  }

  @Override
  protected void finishedNoDocument(Controller ctrl, Throwable t) {
    // nothing to do
  }

}
