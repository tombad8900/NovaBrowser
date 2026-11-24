const fs=require('fs'),vm=require('vm'),assert=require('assert');
const listeners={},native=[],forwarded=[];
function event(name){return {addListener:f=>listeners[name]=f};}
const browser={webRequest:{onSendHeaders:event('send'),onHeadersReceived:event('response'),onCompleted:event('complete'),onErrorOccurred:event('error'),onBeforeRequest:event('before')},tabs:{sendMessage:async(id,m)=>forwarded.push(m),onRemoved:event('removed')},runtime:{sendNativeMessage:async(app,m)=>native.push(m),onMessage:event('message')}};
vm.runInNewContext(fs.readFileSync('app/src/main/assets/nova-ext/background.js','utf8'),{browser,Map,Set,Date});
const base={tabId:1,method:'GET',type:'xmlhttprequest',requestId:'one',url:'https://cdn.test/show/master.m3u8',statusCode:200};
async function test(){
 listeners.before({...base,type:'main_frame'});
 listeners.send({...base,requestHeaders:[{name:'Cookie',value:'session=one'},{name:'Referer',value:'https://site.test/'},{name:'Host',value:'evil'}]});
 await listeners.response({...base,responseHeaders:[{name:'Content-Type',value:'application/vnd.apple.mpegurl'}]});
 assert.equal(native.length,1);assert.equal(native[0].headers.Cookie,'session=one');assert(!native[0].headers.Host);assert(!forwarded[0].headers);assert.equal(typeof forwarded[0].token,'string');
 listeners.send({...base,requestId:'fake',url:'https://cdn.test/fake.m3u8'});
 await listeners.response({...base,requestId:'fake',url:'https://cdn.test/fake.m3u8',responseHeaders:[{name:'Content-Type',value:'text/html'}]});
 assert.equal(native[1].rejected,true);
 listeners.send({...base,requestId:'seg',url:'https://cdn.test/show/a.ts'});
 await listeners.response({...base,requestId:'seg',url:'https://cdn.test/show/a.ts',responseHeaders:[{name:'Content-Type',value:'video/mp2t'}]});
 assert.equal(native[2].segment,true);
 listeners.send({...base,requestId:'audio',url:'https://cdn.test/music/song.m4a'});
 await listeners.response({...base,requestId:'audio',url:'https://cdn.test/music/song.m4a',responseHeaders:[{name:'Content-Type',value:'audio/mp4'}]});
 assert.equal(native[3].segment,false);
 listeners.send({...base,requestId:'audioseg',url:'https://cdn.test/show/chunk.m4a'});
 await listeners.response({...base,requestId:'audioseg',url:'https://cdn.test/show/chunk.m4a',responseHeaders:[{name:'Content-Type',value:'audio/mp4'}]});
 assert.equal(native[4].segment,true);
 const prior=forwarded.length;
 listeners.send({...base,requestId:'stale'});listeners.before({...base,type:'main_frame'});
 await listeners.response({...base,requestId:'stale',responseHeaders:[{name:'Content-Type',value:'video/mp4'}]});
 assert.equal(forwarded.length,prior);
 const hello=await listeners.message({nova:'hello'},{tab:{id:1}});assert.equal(hello.items.length,0);
 await listeners.message({nova:'frameScan',epoch:hello.epoch,urls:['https://frame.test/movie.mp4'],tracks:[]},{tab:{id:1}});
 assert.equal(forwarded.at(-1).nova,'frameScan');assert.equal(forwarded.at(-1).urls[0],'https://frame.test/movie.mp4');
 const count=forwarded.length;await listeners.message({nova:'frameScan',epoch:hello.epoch-1,urls:['https://old.test/movie.mp4']},{tab:{id:1}});assert.equal(forwarded.length,count);
 await listeners.message({nova:'trackHints',epoch:hello.epoch,urls:['https://cdn.test/caption?id=ar']},{tab:{id:1}});
 const countNative=native.length;
 listeners.send({...base,requestId:'caption',url:'https://cdn.test/caption?id=ar',requestHeaders:[{name:'Cookie',value:'sub=one'}]});
 await listeners.response({...base,requestId:'caption',url:'https://cdn.test/caption?id=ar',responseHeaders:[{name:'Content-Type',value:'text/plain'}]});
 assert.equal(native.length,countNative+1);assert.equal(native.at(-1).track,true);assert.equal(native.at(-1).headers.Cookie,'sub=one');
 console.log('PASS detector header privacy, HTML rejection, segment context, navigation generation');
}
test().catch(e=>{console.error(e);process.exit(1)});
