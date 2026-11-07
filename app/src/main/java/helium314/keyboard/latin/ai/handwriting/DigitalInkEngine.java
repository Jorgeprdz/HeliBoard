// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.handwriting;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.vision.digitalink.*;
import java.util.ArrayList;
import java.util.List;
/** ML Kit 18.1.0 adapter. Download is called only by an explicit panel action. */
public final class DigitalInkEngine implements AutoCloseable {
 public interface Status {void done(boolean ready,Exception error);}
 public interface Candidates {void done(List<String> candidates,Exception error);}
 private final DigitalInkRecognitionModel model;private final DigitalInkRecognizer recognizer;
 public DigitalInkEngine(String language) throws Exception {
  DigitalInkRecognitionModelIdentifier id=DigitalInkRecognitionModelIdentifier.fromLanguageTag(language);
  if(id==null)throw new IllegalArgumentException("Unsupported ink language");
  model=DigitalInkRecognitionModel.builder(id).build();recognizer=DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build());
 }
 public void available(Status callback){RemoteModelManager.getInstance().isModelDownloaded(model).addOnSuccessListener(ready->callback.done(ready,null)).addOnFailureListener(e->callback.done(false,e));}
 public void download(Status callback){RemoteModelManager.getInstance().download(model,new DownloadConditions.Builder().build()).addOnSuccessListener(unused->callback.done(true,null)).addOnFailureListener(e->callback.done(false,e));}
 public void recognize(List<List<InkTrace.Point>> strokes,int width,int height,Candidates callback){
  Ink.Builder ink=Ink.builder();for(List<InkTrace.Point> points:strokes){Ink.Stroke.Builder s=Ink.Stroke.builder();for(InkTrace.Point p:points)s.addPoint(Ink.Point.create(p.x,p.y,p.time));ink.addStroke(s.build());}
  RecognitionContext context=RecognitionContext.builder().setWritingArea(new WritingArea(Math.max(1,width),Math.max(1,height))).build();
  recognizer.recognize(ink.build(),context).addOnSuccessListener(result->{List<String> texts=new ArrayList<>();for(RecognitionCandidate c:result.getCandidates())if(!c.getText().isEmpty()&&!texts.contains(c.getText())&&texts.size()<5)texts.add(c.getText());callback.done(texts,null);}).addOnFailureListener(e->callback.done(new ArrayList<>(),e));
 }
 @Override public void close(){recognizer.close();}
}
