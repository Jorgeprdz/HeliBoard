// SPDX-License-Identifier: GPL-3.0-only
#include "llama.h"
#include <atomic>
#include <algorithm>
#include <chrono>
#include <string>
#include <vector>
#include <memory>
#include <mutex>
#include <unordered_map>
#include <fstream>
#include <iostream>
#include <stdexcept>
using Clock=std::chrono::steady_clock;
struct Engine {
 llama_model *model=nullptr; llama_context *ctx=nullptr;std::atomic<bool> stop{false};
 Clock::time_point deadline;
 Engine(const std::string &path){
  static std::once_flag initialized;
  std::call_once(initialized,[]{llama_backend_init();llama_log_set([](ggml_log_level,const char*,void*){},nullptr);});
  auto mp=llama_model_default_params();mp.n_gpu_layers=0;
  model=llama_model_load_from_file(path.c_str(),mp);if(!model)throw std::runtime_error("model unavailable");
  auto cp=llama_context_default_params();cp.n_ctx=1024;cp.n_batch=256;cp.n_ubatch=128;cp.n_threads=2;cp.n_threads_batch=2;
  ctx=llama_init_from_model(model,cp);if(!ctx){llama_model_free(model);model=nullptr;throw std::runtime_error("context unavailable");}
  llama_set_abort_callback(ctx,[](void *p){auto e=static_cast<Engine*>(p);return e->stop.load()||Clock::now()>e->deadline;},this);
 }
 ~Engine(){if(ctx)llama_free(ctx);if(model)llama_model_free(model);}
 std::string generate(const std::string &prompt,int budget){
  deadline=Clock::now()+std::chrono::milliseconds(budget);
  llama_kv_cache_clear(ctx);
  auto vocab=llama_model_get_vocab(model);
  int count=-llama_tokenize(vocab,prompt.data(),prompt.size(),nullptr,0,true,true);
  if(count<=0||count>768)return "";
  std::vector<llama_token> tokens(count);
  if(llama_tokenize(vocab,prompt.data(),prompt.size(),tokens.data(),count,true,true)!=count)return "";
  for(int pos=0;pos<count;pos+=256){
   if(stop.load()||Clock::now()>deadline)return "";
   auto batch=llama_batch_get_one(tokens.data()+pos,std::min(256,count-pos));
   if(llama_decode(ctx,batch)!=0)return "";
  }
  std::unique_ptr<llama_sampler,decltype(&llama_sampler_free)> sampler(llama_sampler_init_greedy(),llama_sampler_free);
  std::string output;
  for(int n=0;n<160;n++){
   if(stop.load()||Clock::now()>deadline)return "";
   llama_token token=llama_sampler_sample(sampler.get(),ctx,-1);
   if(llama_vocab_is_eog(vocab,token))return output;
   char bytes[256];int length=llama_token_to_piece(vocab,token,bytes,sizeof(bytes),0,false);
   if(length<0||length>256||output.size()+length>2560)return "";
   output.append(bytes,length);
   auto batch=llama_batch_get_one(&token,1);if(llama_decode(ctx,batch)!=0)return "";
  }
  return ""; // Never deliver truncated model output.
 }
};
#ifdef __ANDROID__
#include <jni.h>
static std::mutex handlesMutex;
static std::unordered_map<long,std::shared_ptr<Engine>> handles;
static long nextHandle=1;
static std::shared_ptr<Engine> get(long id){std::lock_guard<std::mutex> lock(handlesMutex);auto it=handles.find(id);return it==handles.end()?nullptr:it->second;}
static std::string bytes(JNIEnv *env,jbyteArray value){if(!value)return "";jsize size=env->GetArrayLength(value);std::string out(size,'\0');env->GetByteArrayRegion(value,0,size,reinterpret_cast<jbyte*>(&out[0]));return out;}
extern "C" JNIEXPORT jlong JNICALL Java_helium314_keyboard_latin_ai_local_NativeWritingModel_open(JNIEnv *env,jclass,jbyteArray path){
 try{auto engine=std::make_shared<Engine>(bytes(env,path));std::lock_guard<std::mutex> lock(handlesMutex);long id=nextHandle++;handles[id]=engine;return id;}catch(...){return 0;}
}
extern "C" JNIEXPORT void JNICALL Java_helium314_keyboard_latin_ai_local_NativeWritingModel_reset(JNIEnv*,jclass,jlong id){auto e=get(id);if(e)e->stop=false;}
extern "C" JNIEXPORT void JNICALL Java_helium314_keyboard_latin_ai_local_NativeWritingModel_cancel(JNIEnv*,jclass,jlong id){auto e=get(id);if(e)e->stop=true;}
extern "C" JNIEXPORT void JNICALL Java_helium314_keyboard_latin_ai_local_NativeWritingModel_release(JNIEnv*,jclass,jlong id){
 std::shared_ptr<Engine> old;{std::lock_guard<std::mutex> lock(handlesMutex);auto it=handles.find(id);if(it==handles.end())return;old=it->second;old->stop=true;handles.erase(it);}
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_helium314_keyboard_latin_ai_local_NativeWritingModel_generate(JNIEnv *env,jclass,jlong id,jbyteArray prompt){
 auto e=get(id);std::string output;
 try{if(e)output=e->generate(bytes(env,prompt),12000);}catch(...){output="";}
 jbyteArray result=env->NewByteArray(output.size());if(result)env->SetByteArrayRegion(result,0,output.size(),reinterpret_cast<const jbyte*>(output.data()));return result;
}
#else
#include <sys/resource.h>
int main(int argc,char **argv){
 if(argc!=3)return 2;
 auto start=Clock::now();
 try{
  std::ifstream file(argv[2]);std::string prompt((std::istreambuf_iterator<char>(file)),{});
  Engine engine(argv[1]);std::string result=engine.generate(prompt,30000);std::cout<<result;
  struct rusage usage{};getrusage(RUSAGE_SELF,&usage);
  std::cerr<<"{\"elapsedMs\":"<<std::chrono::duration_cast<std::chrono::milliseconds>(Clock::now()-start).count()<<",\"peakRssKiB\":"<<usage.ru_maxrss<<"}\n";
  return result.empty()?3:0;
 }catch(...){return 4;}
}
#endif
