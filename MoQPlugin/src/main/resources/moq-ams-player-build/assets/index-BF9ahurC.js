(function(){const e=document.createElement("link").relList;if(e&&e.supports&&e.supports("modulepreload"))return;for(const i of document.querySelectorAll('link[rel="modulepreload"]'))r(i);new MutationObserver(i=>{for(const n of i)if(n.type==="childList")for(const a of n.addedNodes)a.tagName==="LINK"&&a.rel==="modulepreload"&&r(a)}).observe(document,{childList:!0,subtree:!0});function s(i){const n={};return i.integrity&&(n.integrity=i.integrity),i.referrerPolicy&&(n.referrerPolicy=i.referrerPolicy),i.crossOrigin==="use-credentials"?n.credentials="include":i.crossOrigin==="anonymous"?n.credentials="omit":n.credentials="same-origin",n}function r(i){if(i.ep)return;i.ep=!0;const n=s(i);fetch(i.href,n)}})();const hn={},fn=typeof hn<"u"&&!1,Ls=Symbol.for("@moq/signals");class f{#e;#t=new Set;#s=new Set;#r=!1;#i;#n=!1;#a=!1;[Ls]=!0;constructor(e){this.#e=e}static from(e){return typeof e=="object"&&e!==null&&Ls in e?e:new f(e)}get(){return this.#e}peek(){return this.#e}set(e,s){this.#n||(this.#i=this.#e,this.#n=!0),this.#e=e,s!==!1&&(s===!0&&(this.#a=!0),!(this.#t.size===0&&this.#s.size===0)&&(this.#r||(this.#r=!0,queueMicrotask(()=>this.#o()))))}#o(){this.#r=!1,this.#n=!1;const e=this.#i;this.#i=void 0;const s=this.#a;if(this.#a=!1,!s&&Yr(e,this.#e))return;const r=this.#e,i=this.#s;this.#s=new Set;for(const n of this.#t)try{n(r)}catch(a){console.error("signal subscriber error",a)}for(const n of i)try{n(r)}catch(a){console.error("signal changed error",a)}}update(e,s=!0){const r=e(this.#e);this.set(r,s)}mutate(e,s=!0){const r=e(this.#e);return this.set(this.#e,s),r}subscribe(e){return this.#t.add(e),()=>this.#t.delete(e)}changed(e){return this.#s.add(e),()=>this.#s.delete(e)}watch(e){const s=this.subscribe(e);return queueMicrotask(()=>e(this.#e)),s}static async race(...e){const s=[],r=await new Promise(i=>{for(const n of e)s.push(n.changed(i))});for(const i of s)i();return r}}class C{static#e=new FinalizationRegistry(e=>{console.warn(`Signals was garbage collected without being closed:
${e}`)});#t;#s=[];#r=[];#i=[];#n;#a=!1;#o;#c;#d;#l;#u=new AbortController;constructor(e){this.#t=e,this.#c=new Promise(s=>{this.#o=s}),this.#l=new Promise(s=>{this.#d=s}),e&&this.#h()}#h(){this.#a||(this.#a=!0,queueMicrotask(()=>this.#f().catch(e=>{console.error("effect error",e,this.#n)})))}async#f(){if(this.#s!==void 0){this.#o(),this.#u.abort(),this.#u=new AbortController,this.#c=new Promise(e=>{this.#o=e});for(const e of this.#r)e();this.#r.length=0;for(const e of this.#s)e();if(this.#s.length=0,this.#i.length>0)try{let e;const s=new Promise(r=>{e=setTimeout(()=>{r()},5e3)});await Promise.race([Promise.all(this.#i),s]),e&&clearTimeout(e),this.#i.length=0}catch(e){console.error("async effect error",e),this.#n&&console.error("stack",this.#n)}this.#s!==void 0&&(this.#a=!1,this.#t&&this.#t(this))}}get(e){if(this.#s===void 0)return e.peek();const s=e.peek(),r=e.changed(()=>this.#h());return this.#r.push(r),s}set(e,s,...r){if(this.#s===void 0)return;e.set(s);const i=r[0],n=i===void 0?void 0:i;this.cleanup(()=>e.set(n))}spawn(e){const s=e().catch(r=>{console.error("spawn error",r)});this.#s!==void 0&&this.#i.push(s)}timer(e,s){if(this.#s===void 0)return;let r;r=setTimeout(()=>{r=void 0,e()},s),this.cleanup(()=>r&&clearTimeout(r))}timeout(e,s){if(this.#s===void 0)return;const r=new C(e);let i=setTimeout(()=>{r.close(),i=void 0},s);this.#s.push(()=>{i&&(clearTimeout(i),r.close())})}animate(e){if(this.#s===void 0)return;let s=requestAnimationFrame(r=>{e(r),s=void 0});this.cleanup(()=>{s&&cancelAnimationFrame(s)})}interval(e,s){if(this.#s===void 0)return;const r=setInterval(()=>{e()},s);this.cleanup(()=>clearInterval(r))}run(e){if(this.#s===void 0)return;const s=new C(e);this.#s.push(()=>s.close())}effect(e){return this.run(e)}getAll(e){const s=[];for(const r of e){const i=this.get(r);if(!i)return;s.push(i)}return s}subscribe(e,s){if(this.#s===void 0){s(e.peek());return}this.run(r=>{const i=r.get(e);s(i)})}event(e,s,r,i){if(this.#s===void 0)return;const n=typeof i!="boolean"&&i?.signal?AbortSignal.any([this.#u.signal,i.signal]):this.#u.signal,a=typeof i=="boolean"?{capture:i,signal:n}:{...i,signal:n};e.addEventListener(s,r,a)}cleanup(e){if(this.#s===void 0){e();return}this.#s.push(e)}close(){if(this.#s!==void 0){this.#d(),this.#o(),this.#u.abort();for(const e of this.#s)e();this.#s=void 0;for(const e of this.#r)e();this.#r.length=0,this.#i.length=0}}get closed(){return this.#l}get cancel(){return this.#c}get abort(){return this.#u.signal}proxy(e,s){this.subscribe(s,r=>e.update(()=>r))}}function Yr(t,e){if(t===e)return!0;if(t===null||e===null||typeof t!="object"||typeof e!="object")return!1;const s=Object.getPrototypeOf(t),r=Object.getPrototypeOf(e);if(s!==r||s!==Object.prototype&&s!==Array.prototype)return!1;const i=Object.keys(t),n=Object.keys(e);if(i.length!==n.length)return!1;for(const a of i)if(!Yr(t[a],e[a]))return!1;return!0}function Ze(...t){return t.join("/").replace(/\/+/g,"/").replace(/^\/+/,"").replace(/\/+$/,"")}function pn(t,e){return t===""?!0:e.startsWith(t)?e.length===t.length?!0:e[t.length]==="/":!1}function Lt(t,e){return pn(t,e)?t===""?e:e.length===t.length?"":e.slice(t.length+1):null}function Bt(t,e){return t===""?e:e===""?t:`${t}/${e}`}function wt(){return""}class wn{queue=new f([]);closed=new f(!1)}class xs{state=new wn;prefix;closed;constructor(e=wt()){this.prefix=e,this.closed=new Promise(s=>{const r=this.state.closed.subscribe(i=>{i&&(s(i instanceof Error?i:void 0),r())})})}append(e){if(this.state.closed.peek())throw new Error("announced is closed");this.state.queue.mutate(s=>{s.push(e)})}close(e){this.state.closed.set(e??!0),this.state.queue.mutate(s=>{s.length=0})}async next(){for(;;){const e=this.state.queue.peek().shift();if(e)return e;const s=this.state.closed.peek();if(s instanceof Error)throw s;if(s)return;await f.race(this.state.queue,this.state.closed)}}}class mn{frames=new f([]);closed=new f(!1);total=new f(0)}let Ts=class{sequence;state=new mn;closed;constructor(e){this.sequence=e,this.closed=new Promise(s=>{const r=this.state.closed.subscribe(i=>{i&&(s(i instanceof Error?i:void 0),r())})})}writeFrame(e){if(this.state.closed.peek())throw new Error("group is closed");this.state.frames.mutate(s=>{s.push(e)}),this.state.total.update(s=>s+1)}writeString(e){this.writeFrame(new TextEncoder().encode(e))}writeJson(e){this.writeString(JSON.stringify(e))}writeBool(e){this.writeFrame(new Uint8Array([e?1:0]))}async readFrame(){for(;;){const s=this.state.frames.peek().shift();if(s)return s;const r=this.state.closed.peek();if(r instanceof Error)throw r;if(r)return;await f.race(this.state.frames,this.state.closed)}}async readFrameSequence(){for(;;){const e=this.state.frames.peek(),s=e.shift();if(s)return{sequence:this.state.total.peek()-e.length-1,data:s};const r=this.state.closed.peek();if(r instanceof Error)throw r;if(r)return;await f.race(this.state.frames,this.state.closed)}}async readString(){const e=await this.readFrame();return e?new TextDecoder().decode(e):void 0}async readJson(){const e=await this.readString();return e?JSON.parse(e):void 0}async readBool(){const e=await this.readFrame();return e?e[0]===1:void 0}close(e){this.state.closed.set(e??!0)}};class bn{groups=new f([]);closed=new f(!1)}class gn{name;state=new bn;#e;closed;constructor(e){this.name=e,this.closed=new Promise(s=>{const r=this.state.closed.subscribe(i=>{i&&(s(i instanceof Error?i:void 0),r())})})}appendGroup(){if(this.state.closed.peek())throw new Error("track is closed");const e=new Ts(this.#e??0);return this.#e=e.sequence+1,this.state.groups.mutate(s=>{s.push(e),s.sort((r,i)=>r.sequence-i.sequence)}),e}writeGroup(e){if(this.state.closed.peek())throw new Error("track is closed");e.sequence>=(this.#e??0)&&(this.#e=e.sequence+1),this.state.groups.mutate(s=>{s.push(e),s.sort((r,i)=>r.sequence-i.sequence)})}writeFrame(e){const s=this.appendGroup();s.writeFrame(e),s.close()}writeString(e){const s=this.appendGroup();s.writeString(e),s.close()}writeJson(e){const s=this.appendGroup();s.writeJson(e),s.close()}writeBool(e){const s=this.appendGroup();s.writeBool(e),s.close()}async nextGroup(){for(;;){const e=this.state.groups.peek();if(e.length>0)return e.shift();const s=this.state.closed.peek();if(s instanceof Error)throw s;if(s)return;await f.race(this.state.groups,this.state.closed)}}async readFrame(){return(await this.readFrameSequence())?.data}async readFrameSequence(){for(;;){const e=this.state.groups.peek();for(;e.length>1;){const a=e[0].state.frames.peek(),o=a.shift();if(o){const c=e[0].state.total.peek()-a.length-1;return{group:e[0].sequence,frame:c,data:o}}e.shift()?.close()}if(e.length===0){const a=this.state.closed.peek();if(a instanceof Error)throw a;if(a)return;await f.race(this.state.groups,this.state.closed);continue}const s=e[0],r=s.state.frames.peek(),i=r.shift();if(i){const a=s.state.total.peek()-r.length-1;return{group:s.sequence,frame:a,data:i}}const n=this.state.closed.peek();if(n instanceof Error)throw n;if(n)return;await f.race(this.state.groups,this.state.closed,s.state.frames)}}async readString(){const e=await this.readFrame();if(e)return new TextDecoder().decode(e)}async readJson(){const e=await this.readString();if(e)return JSON.parse(e)}async readBool(){const e=await this.readFrame();if(e){if(e.byteLength!==1||!(e[0]===0||e[0]===1))throw new Error("invalid bool frame");return e[0]===1}}close(e){this.state.closed.set(e??!0);for(const s of this.state.groups.peek())s.close(e)}}class yn{requested=new f([]);closed=new f(!1)}class Qr{state=new yn;closed;constructor(){this.closed=new Promise(e=>{const s=this.state.closed.subscribe(r=>{r&&(e(r instanceof Error?r:void 0),s())})})}async requested(){for(;;){const e=this.state.requested.peek().pop();if(e)return e;const s=this.state.closed.peek();if(s instanceof Error)throw s;if(s)return;await f.race(this.state.requested,this.state.closed)}}subscribe(e,s){const r=new gn(e);if(this.state.closed.peek())throw new Error(`broadcast is closed: ${this.state.closed.peek()}`);return this.state.requested.mutate(i=>{i.push({track:r,priority:s}),i.sort((n,a)=>n.priority-a.priority)}),r}close(e){this.state.closed.set(e??!0);for(const{track:s}of this.state.requested.peek())s.close(e);this.state.requested.mutate(s=>{s.length=0})}}class M{static MAX=(1n<<62n)-1n;static MAX_SIZE=8;value;constructor(e){if(e<0n||e>M.MAX)throw new Error(`VarInt value out of range: ${e}`);this.value=e}static from(e){return new M(BigInt(e))}size(){const e=this.value;if(e<2n**6n)return 1;if(e<2n**14n)return 2;if(e<2n**30n)return 4;if(e<2n**62n)return 8;throw new Error("VarInt value too large")}encode(e){const s=this.value,r=this.size();if(e.byteOffset+e.byteLength+r>e.buffer.byteLength)throw new Error("destination buffer too small");const i=new DataView(e.buffer,e.byteOffset+e.byteLength,r);if(r===1)i.setUint8(0,Number(s));else if(r===2)i.setUint16(0,16384|Number(s),!1);else if(r===4)i.setUint32(0,2<<30|Number(s),!1);else if(r===8)i.setBigUint64(0,3n<<62n|s,!1);else throw new Error("VarInt value too large");return new Uint8Array(e.buffer,e.byteOffset,e.byteLength+r)}static decode(e){if(e.byteLength<1)throw new Error("Unexpected end of buffer");const s=new DataView(e.buffer,e.byteOffset),r=s.getUint8(0),i=r>>6;let n,a;switch(i){case 0:n=BigInt(r&63),a=1;break;case 1:if(2>e.length)throw new Error("Unexpected end of buffer");n=BigInt(s.getUint16(0,!1)&16383),a=2;break;case 2:if(4>e.length)throw new Error("Unexpected end of buffer");n=BigInt(s.getUint32(0,!1)&1073741823),a=4;break;case 3:if(8>e.length)throw new Error("Unexpected end of buffer");n=s.getBigUint64(0,!1)&0x3fffffffffffffffn,a=8;break;default:throw new Error("Invalid VarInt tag")}const o=new Uint8Array(e.buffer,e.byteOffset+a,e.byteLength-a);return[new M(n),o]}}const Se={Bi:0,Uni:1};class Be{value;constructor(e){this.value=e}static create(e,s,r){let i=e<<2n;return s===Se.Uni&&(i|=0x02n),r&&(i|=0x01n),new Be(M.from(i))}get dir(){return(this.value.value&0x02n)!==0n?Se.Uni:Se.Bi}get serverInitiated(){return(this.value.value&0x01n)!==0n}canRecv(e){return this.dir===Se.Uni?this.serverInitiated!==e:!0}canSend(e){return this.dir===Se.Uni?this.serverInitiated===e:!0}}const ei=4,ti=5,si=8,ps=9,ri=29;function Bs(t){switch(t.type){case"stream":{let e=new Uint8Array(new ArrayBuffer(9+t.data.length),0,1);return e[0]=t.fin?ps:si,e=t.id.value.encode(e),e=new Uint8Array(e.buffer,e.byteOffset,e.byteLength+t.data.length),e.set(t.data,e.byteLength-t.data.length),e}case"reset_stream":{let e=new Uint8Array(new ArrayBuffer(17),0,1);return e[0]=ei,e=t.id.value.encode(e),e=t.code.encode(e),e}case"stop_sending":{let e=new Uint8Array(new ArrayBuffer(17),0,1);return e[0]=ti,e=t.id.value.encode(e),e=t.code.encode(e),e}case"connection_close":{const e=new TextEncoder().encode(t.reason);let s=new Uint8Array(new ArrayBuffer(9+e.length),0,1);return s[0]=ri,s=t.code.encode(s),s=new Uint8Array(s.buffer,s.byteOffset,s.byteLength+e.length),s.set(e,s.byteLength-e.length),s}}}function vn(t){if(t.length===0)throw new Error("Invalid frame: empty buffer");const e=t[0];t=t.slice(1);let s;if(e===ei){[s,t]=M.decode(t);const r=new Be(s);return[s,t]=M.decode(t),{type:"reset_stream",id:r,code:s}}if(e===ti){[s,t]=M.decode(t);const r=new Be(s);return[s,t]=M.decode(t),{type:"stop_sending",id:r,code:s}}if(e===ri){[s,t]=M.decode(t);const r=s,i=new TextDecoder().decode(t);return{type:"connection_close",code:r,reason:i}}if(e===si||e===ps)return[s,t]=M.decode(t),{type:"stream",id:new Be(s),data:t,fin:e===ps};throw new Error(`Invalid frame type: ${e}`)}class _n{incomingHighWaterMark;incomingMaxAge;maxDatagramSize;outgoingHighWaterMark;outgoingMaxAge;readable;writable;constructor(){this.incomingHighWaterMark=1024,this.incomingMaxAge=null,this.maxDatagramSize=1200,this.outgoingHighWaterMark=1024,this.outgoingMaxAge=null,this.readable=new ReadableStream({}),this.writable=new WritableStream({})}}class Kt{#e;#t=!1;#s;#r;#i=new Map;#n=new Map;#a=0n;#o=0n;ready;#c;closed;#d;incomingBidirectionalStreams;#l;incomingUnidirectionalStreams;#u;datagrams=new _n;constructor(e,s){if(s?.requireUnreliable)throw new Error("not allowed to use WebSocket; requireUnreliable is true");if(s?.serverCertificateHashes&&console.warn("serverCertificateHashes is not supported; trying anyway"),e=Kt.#h(e),this.#e=new WebSocket(e,["webtransport"]),this.ready=new Promise(r=>{this.#c=r}),this.closed=new Promise(r=>{this.#d=r}),this.#e.binaryType="arraybuffer",this.#e.onopen=()=>this.#c(),this.#e.onmessage=r=>this.#f(r),this.#e.onerror=r=>this.#b(r),this.#e.onclose=r=>this.#m(r),this.incomingBidirectionalStreams=new ReadableStream({start:r=>{this.#l=r}}),this.incomingUnidirectionalStreams=new ReadableStream({start:r=>{this.#u=r}}),!this.#l||!this.#u)throw new Error("ReadableStream didn't call start")}static#h(e){const s=typeof e=="string"?new URL(e):e;let r=s.protocol;if(r==="https:")r="wss:";else if(r==="http:")r="ws:";else if(r!=="ws:"&&r!=="wss:")throw new Error(`Unsupported protocol: ${r}`);return`${r}//${s.host}${s.pathname}${s.search}`}#f(e){if(!(e.data instanceof ArrayBuffer))return;const s=new Uint8Array(e.data);try{const r=vn(s);this.#g(r)}catch(r){console.error("Failed to decode frame:",r),this.close({closeCode:1002,reason:"Protocol violation"})}}#b(e){this.#s||(this.#s=new Error(`WebSocket error: ${e.type}`),this.#v(1006,"WebSocket error"))}#m(e){this.#s||(this.#s=new Error(`Connection closed: ${e.code} ${e.reason}`),this.#v(e.code,e.reason))}#g(e){if(e.type==="stream")this.#y(e);else if(e.type==="reset_stream")this.#_(e);else if(e.type==="stop_sending")this.#k(e);else if(e.type==="connection_close")this.#r=new Error(`Connection closed: ${e.code.value}: ${e.reason}`),this.#e.close();else{const s=e;throw new Error(`Unknown frame type: ${s}`)}}async#y(e){const s=e.id.value.value;if(!e.id.canRecv(this.#t))throw new Error("Invalid stream ID direction");let r=this.#n.get(s);if(!r){if(e.id.serverInitiated===this.#t)return;if(!e.id.canRecv(this.#t))throw new Error("received write-only stream");const i=new ReadableStream({start:n=>{r=n,this.#n.set(s,n)},cancel:()=>{this.#p({type:"stop_sending",id:e.id,code:M.from(0)}),this.#n.delete(s)}});if(!r)throw new Error("ReadableStream didn't call start");if(e.id.dir===Se.Bi){const n=new WritableStream({start:a=>{this.#i.set(s,a)},write:async a=>{await Promise.race([this.#w({type:"stream",id:e.id,data:a,fin:!1}),this.closed])},abort:a=>{console.warn("abort",a),this.#p({type:"reset_stream",id:e.id,code:M.from(0)}),this.#i.delete(s)},close:async()=>{await Promise.race([this.#w({type:"stream",id:e.id,data:new Uint8Array,fin:!0}),this.closed]),this.#i.delete(s)}});this.#l.enqueue({readable:i,writable:n})}else this.#u.enqueue(i)}e.data.byteLength>0&&r.enqueue(e.data),e.fin&&(r.close(),this.#n.delete(s))}#_(e){const s=e.id.value.value,r=this.#n.get(s);r&&(r.error(new Error(`RESET_STREAM: ${e.code.value}`)),this.#n.delete(s))}#k(e){const s=e.id.value.value,r=this.#i.get(s);r&&(r.error(new Error(`STOP_SENDING: ${e.code.value}`)),this.#i.delete(s),this.#p({type:"reset_stream",id:e.id,code:e.code}))}async#w(e){for(;this.#e.bufferedAmount>64*1024;)await new Promise(r=>setTimeout(r,10));const s=Bs(e);this.#e.send(s)}#p(e){const s=Bs(e);this.#e.send(s)}async createBidirectionalStream(){if(await this.ready,this.#s)throw this.#r||new Error("Connection closed");const e=Be.create(this.#o++,Se.Bi,this.#t),s=new WritableStream({start:i=>{this.#i.set(e.value.value,i)},write:async i=>{await Promise.race([this.#w({type:"stream",id:e,data:i,fin:!1}),this.closed])},abort:i=>{console.warn("abort",i),this.#p({type:"reset_stream",id:e,code:M.from(0)}),this.#i.delete(e.value.value)},close:async()=>{await Promise.race([this.#w({type:"stream",id:e,data:new Uint8Array,fin:!0}),this.closed]),this.#i.delete(e.value.value)}});return{readable:new ReadableStream({start:i=>{this.#n.set(e.value.value,i)},cancel:async()=>{this.#p({type:"stop_sending",id:e,code:M.from(0)}),this.#n.delete(e.value.value)}}),writable:s}}async createUnidirectionalStream(){if(await this.ready,this.#s)throw this.#s;const e=Be.create(this.#a++,Se.Uni,this.#t),s=this;return new WritableStream({start:i=>{s.#i.set(e.value.value,i)},async write(i){await Promise.race([s.#w({type:"stream",id:e,data:i,fin:!1}),s.closed])},abort(i){console.warn("abort",i),s.#p({type:"reset_stream",id:e,code:M.from(0)}),s.#i.delete(e.value.value)},async close(){await Promise.race([s.#w({type:"stream",id:e,data:new Uint8Array,fin:!0}),s.closed]),s.#i.delete(e.value.value)}})}#v(e,s){this.#d({closeCode:e,reason:s});try{this.#l.close()}catch{}try{this.#u.close()}catch{}for(const r of this.#i.values())try{r.error(this.#s)}catch{}for(const r of this.#n.values())try{r.error(this.#s)}catch{}this.#i.clear(),this.#n.clear()}close(e){if(this.#s)return;const s=e?.closeCode??0,r=e?.reason??"";this.#p({type:"connection_close",code:M.from(s),reason:r}),setTimeout(()=>{this.#e.close()},100),this.#v(s,r)}get congestionControl(){return"default"}}const kn=2**6-1,En=2**14-1,In=2**30-1,ii=Number.MAX_SAFE_INTEGER;function Sn(t,e){const s=new Uint8Array(t,0,1);return s[0]=e,s}function An(t,e){const s=new DataView(t,0,2);return s.setUint16(0,e),new Uint8Array(s.buffer,s.byteOffset,s.byteLength)}function xn(t,e){const s=new DataView(t,0,4);return s.setUint32(0,e),new Uint8Array(s.buffer,s.byteOffset,s.byteLength)}function Tn(t,e){const s=new DataView(t,0,8);return s.setBigUint64(0,e),new Uint8Array(s.buffer,s.byteOffset,s.byteLength)}const Rn=2n**62n-1n;function ws(t,e){const s=BigInt(e);if(s<0n)throw new Error(`underflow, value is negative: ${e}`);if(s>Rn)throw new Error(`overflow, value larger than 62-bits: ${e}`);const r=Number(s);return r<=kn?Sn(t,r):r<=En?An(t,r|16384):r<=In?xn(t,r|2147483648):Tn(t,s|0xc000000000000000n)}function Vs(t){return ws(new ArrayBuffer(8),t)}function ms(t){if(t.length===0)throw new Error("buffer is empty");const e=1<<((t[0]&192)>>6);if(t.length<e)throw new Error(`buffer too short: need ${e} bytes, have ${t.length}`);const s=new DataView(t.buffer,t.byteOffset,e),r=t.subarray(e);let i;if(e===1)i=t[0]&63;else if(e===2)i=s.getUint16(0)&16383;else if(e===4)i=s.getUint32(0)&1073741823;else if(e===8)i=Number(s.getBigUint64(0)&0x3fffffffffffffffn);else throw new Error("impossible");return[i,r]}const Pn=2**31-1,js=1024*1024*64;let ot=class bs{reader;writer;constructor(e){this.writer=new Ke(e.writable),this.reader=new Yt(e.readable)}static async accept(e){for(;;){const s=e.incomingBidirectionalStreams.getReader(),r=await s.read();return s.releaseLock(),r.done?void 0:new bs(r.value)}}static async open(e,s){return new bs(await e.createBidirectionalStream({sendOrder:s}))}close(){this.writer.close(),this.reader.stop(new Error("cancel"))}abort(e){this.writer.reset(e),this.reader.stop(e)}};class Yt{#e;#t;#s;constructor(e,s){this.#e=s??new Uint8Array,this.#t=e,this.#s=this.#t?.getReader()}async#r(){if(!this.#s)return!1;const e=await this.#s.read();if(e.done)return!1;if(e.value.byteLength===0)throw new Error("unexpected empty chunk");const s=new Uint8Array(e.value);if(this.#e.byteLength===0)this.#e=s;else{const r=new Uint8Array(this.#e.byteLength+s.byteLength);r.set(this.#e),r.set(s,this.#e.byteLength),this.#e=r}return!0}async#i(e){if(e>js)throw new Error(`read size ${e} exceeds max size ${js}`);for(;this.#e.byteLength<e;)if(!await this.#r())throw new Error("unexpected end of stream")}#n(e){const s=new Uint8Array(this.#e.buffer,this.#e.byteOffset,e);return this.#e=new Uint8Array(this.#e.buffer,this.#e.byteOffset+e,this.#e.byteLength-e),s}async read(e){return e===0?new Uint8Array:(await this.#i(e),this.#n(e))}async readAll(){for(;await this.#r(););return this.#n(this.#e.byteLength)}async string(){const e=await this.u53(),s=await this.read(e);return new TextDecoder().decode(s)}async bool(){const e=await this.u8();if(e===0)return!1;if(e===1)return!0;throw new Error("invalid bool value")}async u8(){return await this.#i(1),this.#n(1)[0]}async u16(){await this.#i(2);const s=new DataView(this.#e.buffer,this.#e.byteOffset,2).getUint16(0);return this.#n(2),s}async u53(){const e=await this.u62();if(e>ii)throw new Error("value larger than 53-bits; use v62 instead");return Number(e)}async u62(){await this.#i(1);const e=(this.#e[0]&192)>>6;if(e===0){const i=this.#n(1)[0];return BigInt(i)&0x3fn}if(e===1){await this.#i(2);const i=this.#n(2),n=new DataView(i.buffer,i.byteOffset,i.byteLength);return BigInt(n.getUint16(0))&0x3fffn}if(e===2){await this.#i(4);const i=this.#n(4),n=new DataView(i.buffer,i.byteOffset,i.byteLength);return BigInt(n.getUint32(0))&0x3fffffffn}await this.#i(8);const s=this.#n(8);return new DataView(s.buffer,s.byteOffset,s.byteLength).getBigUint64(0)&0x3fffffffffffffffn}async done(){return this.#e.byteLength>0?!1:!await this.#r()}stop(e){this.#s?.cancel(e).catch(()=>{})}get closed(){return this.#s?.closed??Promise.resolve()}}class Ke{#e;#t;#s;constructor(e){this.#t=e,this.#s=new ArrayBuffer(8),this.#e=this.#t.getWriter()}async bool(e){await this.write(Gs(this.#s,e?1:0))}async u8(e){await this.write(Gs(this.#s,e))}async u16(e){await this.write(On(this.#s,e))}async i32(e){if(Math.abs(e)>Pn)throw new Error(`overflow, value larger than 32-bits: ${e.toString()}`);await this.write(Nn(this.#s,e))}async u53(e){if(e>ii)throw new Error(`overflow, value larger than 53-bits: ${e.toString()}`);await this.write(ws(this.#s,e))}async u62(e){await this.write(ws(this.#s,e))}async write(e){await this.#e.write(e)}async string(e){const s=new TextEncoder().encode(e);await this.u53(s.byteLength),await this.write(s)}close(){this.#e.close().catch(()=>{})}get closed(){return this.#e.closed}reset(e){this.#e.abort(e).catch(()=>{})}static async open(e){const s=await e.createUnidirectionalStream();return new Ke(s)}}function Gs(t,e){const s=new Uint8Array(t,0,1);return s[0]=e,s}function On(t,e){const s=new DataView(t,0,2);return s.setUint16(0,e),new Uint8Array(s.buffer,s.byteOffset,s.byteLength)}function Nn(t,e){const s=new DataView(t,0,4);return s.setInt32(0,e),new Uint8Array(s.buffer,s.byteOffset,s.byteLength)}class ni{#e;constructor(e){this.#e=e.incomingUnidirectionalStreams.getReader()}async next(){const e=await this.#e.read();if(!e.done)return new Yt(e.value)}close(){this.#e.cancel()}}function B(t){return t instanceof Error?t:new Error(String(t))}function S(t){throw new Error(`unreachable: ${t}`)}const Un=new Error("request for lock canceled");var zn=function(t,e,s,r){function i(n){return n instanceof s?n:new s(function(a){a(n)})}return new(s||(s=Promise))(function(n,a){function o(d){try{u(r.next(d))}catch(h){a(h)}}function c(d){try{u(r.throw(d))}catch(h){a(h)}}function u(d){d.done?n(d.value):i(d.value).then(o,c)}u((r=r.apply(t,e||[])).next())})};class qn{constructor(e,s=Un){this._value=e,this._cancelError=s,this._queue=[],this._weightedWaiters=[]}acquire(e=1,s=0){if(e<=0)throw new Error(`invalid weight ${e}: must be positive`);return new Promise((r,i)=>{const n={resolve:r,reject:i,weight:e,priority:s},a=ai(this._queue,o=>s<=o.priority);a===-1&&e<=this._value?this._dispatchItem(n):this._queue.splice(a+1,0,n)})}runExclusive(e){return zn(this,arguments,void 0,function*(s,r=1,i=0){const[n,a]=yield this.acquire(r,i);try{return yield s(n)}finally{a()}})}waitForUnlock(e=1,s=0){if(e<=0)throw new Error(`invalid weight ${e}: must be positive`);return this._couldLockImmediately(e,s)?Promise.resolve():new Promise(r=>{this._weightedWaiters[e-1]||(this._weightedWaiters[e-1]=[]),$n(this._weightedWaiters[e-1],{resolve:r,priority:s})})}isLocked(){return this._value<=0}getValue(){return this._value}setValue(e){this._value=e,this._dispatchQueue()}release(e=1){if(e<=0)throw new Error(`invalid weight ${e}: must be positive`);this._value+=e,this._dispatchQueue()}cancel(){this._queue.forEach(e=>e.reject(this._cancelError)),this._queue=[]}_dispatchQueue(){for(this._drainUnlockWaiters();this._queue.length>0&&this._queue[0].weight<=this._value;)this._dispatchItem(this._queue.shift()),this._drainUnlockWaiters()}_dispatchItem(e){const s=this._value;this._value-=e.weight,e.resolve([s,this._newReleaser(e.weight)])}_newReleaser(e){let s=!1;return()=>{s||(s=!0,this.release(e))}}_drainUnlockWaiters(){if(this._queue.length===0)for(let e=this._value;e>0;e--){const s=this._weightedWaiters[e-1];s&&(s.forEach(r=>r.resolve()),this._weightedWaiters[e-1]=[])}else{const e=this._queue[0].priority;for(let s=this._value;s>0;s--){const r=this._weightedWaiters[s-1];if(!r)continue;const i=r.findIndex(n=>n.priority<=e);(i===-1?r:r.splice(0,i)).forEach((n=>n.resolve()))}}}_couldLockImmediately(e,s){return(this._queue.length===0||this._queue[0].priority<s)&&e<=this._value}}function $n(t,e){const s=ai(t,r=>e.priority<=r.priority);t.splice(s+1,0,e)}function ai(t,e){for(let s=t.length-1;s>=0;s--)if(e(t[s]))return s;return-1}var Cn=function(t,e,s,r){function i(n){return n instanceof s?n:new s(function(a){a(n)})}return new(s||(s=Promise))(function(n,a){function o(d){try{u(r.next(d))}catch(h){a(h)}}function c(d){try{u(r.throw(d))}catch(h){a(h)}}function u(d){d.done?n(d.value):i(d.value).then(o,c)}u((r=r.apply(t,e||[])).next())})};class Zs{constructor(e){this._semaphore=new qn(1,e)}acquire(){return Cn(this,arguments,void 0,function*(e=0){const[,s]=yield this._semaphore.acquire(1,e);return s})}runExclusive(e,s=0){return this._semaphore.runExclusive(()=>e(),1,s)}isLocked(){return this._semaphore.isLocked()}waitForUnlock(e=0){return this._semaphore.waitForUnlock(1,e)}release(){this._semaphore.isLocked()&&this._semaphore.release()}cancel(){return this._semaphore.cancel()}}async function _(t,e){let s=new Uint8Array;const r=new Ke(new WritableStream({write(i){const n=s.byteLength+i.byteLength;if(n>s.buffer.byteLength){const a=Math.max(n,s.buffer.byteLength*2),o=new ArrayBuffer(a),c=new Uint8Array(o,0,n);c.set(s),c.set(i,s.byteLength),s=c}else s=new Uint8Array(s.buffer,0,n),s.set(i,n-i.byteLength)}}));try{await e(r)}finally{r.close()}if(await r.closed,s.byteLength>65535)throw new Error(`Message too large: ${s.byteLength} bytes (max 65535)`);await t.u16(s.byteLength),await t.write(s)}async function k(t,e){const s=await t.u16(),r=await t.read(s),i=new Yt(void 0,r),n=await e(i);if(!await i.done())throw new Error("Message decoding consumed too few bytes");return n}class ge{static id=22;requestId;trackNamespace;trackName;subscriberPriority;groupOrder;startGroup;startObject;endGroup;endObject;constructor({requestId:e,trackNamespace:s,trackName:r,subscriberPriority:i,groupOrder:n,startGroup:a,startObject:o,endGroup:c,endObject:u}){this.requestId=e,this.trackNamespace=s,this.trackName=r,this.subscriberPriority=i,this.groupOrder=n,this.startGroup=a,this.startObject=o,this.endGroup=c,this.endObject=u}async#e(e){throw new Error("FETCH messages are not supported")}async encode(e,s){return _(e,this.#e.bind(this))}static async decode(e,s){return k(e,ge.#t)}static async#t(e){throw new Error("FETCH messages are not supported")}}class ye{static id=24;requestId;constructor({requestId:e}){this.requestId=e}async#e(e){throw new Error("FETCH_OK messages are not supported")}async encode(e,s){return _(e,this.#e.bind(this))}static async decode(e,s){return k(e,ye.#t)}static async#t(e){throw new Error("FETCH_OK messages are not supported")}}class xt{static id=25;requestId;errorCode;reasonPhrase;constructor({requestId:e,errorCode:s,reasonPhrase:r}){this.requestId=e,this.errorCode=s,this.reasonPhrase=r}async#e(e){throw new Error("FETCH_ERROR messages are not supported")}async encode(e,s){return _(e,this.#e.bind(this))}static async decode(e,s){return k(e,xt.#t)}static async#t(e){throw new Error("FETCH_ERROR messages are not supported")}}class ve{static id=23;requestId;constructor({requestId:e}){this.requestId=e}async#e(e){throw new Error("FETCH_CANCEL messages are not supported")}async encode(e,s){return _(e,this.#e.bind(this))}static async decode(e,s){return k(e,ve.#t)}static async#t(e){throw new Error("FETCH_CANCEL messages are not supported")}}class ue{static id=16;newSessionUri;constructor({newSessionUri:e}){this.newSessionUri=e}async#e(e){await e.string(this.newSessionUri)}async encode(e,s){return _(e,this.#e.bind(this))}static async decode(e,s){return k(e,ue.#t)}static async#t(e){const s=await e.string();return new ue({newSessionUri:s})}}async function we(t,e){const s=e.split("/");await t.u53(s.length);for(const r of s)await t.string(r)}async function me(t){const e=[],s=await t.u53();for(let r=0;r<s;r++)e.push(await t.string());return Ze(...e)}const m={DRAFT_07:4278190087,DRAFT_14:4278190094,DRAFT_15:4278190095,DRAFT_16:4278190096},Vt={DRAFT_15:"moqt-15",DRAFT_16:"moqt-16"},rs={MaxRequestId:2n,Implementation:7n};class j{vars;bytes;constructor(){this.vars=new Map,this.bytes=new Map}get size(){return this.vars.size+this.bytes.size}setBytes(e,s){if(e%2n!==1n)throw new Error(`invalid parameter id: ${e.toString()}, must be odd`);this.bytes.set(e,s)}setVarint(e,s){if(e%2n!==0n)throw new Error(`invalid parameter id: ${e.toString()}, must be even`);this.vars.set(e,s)}getBytes(e){if(e%2n!==1n)throw new Error(`invalid parameter id: ${e.toString()}, must be odd`);return this.bytes.get(e)}getVarint(e){if(e%2n!==0n)throw new Error(`invalid parameter id: ${e.toString()}, must be even`);return this.vars.get(e)}removeBytes(e){if(e%2n!==1n)throw new Error(`invalid parameter id: ${e.toString()}, must be odd`);return this.bytes.delete(e)}removeVarint(e){if(e%2n!==0n)throw new Error(`invalid parameter id: ${e.toString()}, must be even`);return this.vars.delete(e)}async encode(e,s){if(await e.u53(this.vars.size+this.bytes.size),s===m.DRAFT_16){const r=[];for(const n of this.vars.keys())r.push({key:n,isVar:!0});for(const n of this.bytes.keys())r.push({key:n,isVar:!1});r.sort((n,a)=>n.key<a.key?-1:n.key>a.key?1:0);let i=0n;for(let n=0;n<r.length;n++){const{key:a,isVar:o}=r[n],c=n===0?a:a-i;if(i=a,await e.u62(c),o)await e.u62(this.vars.get(a));else{const u=this.bytes.get(a);await e.u53(u.length),await e.write(u)}}}else{for(const[r,i]of this.vars)await e.u62(r),await e.u62(i);for(const[r,i]of this.bytes)await e.u62(r),await e.u53(i.length),await e.write(i)}}static async decode(e,s){const r=await e.u53(),i=new j;let n=0n;for(let a=0;a<r;a++){let o;if(s===m.DRAFT_16){const c=await e.u62();o=a===0?c:n+c,n=o}else o=await e.u62();if(o%2n===0n){if(i.vars.has(o))throw new Error(`duplicate parameter id: ${o.toString()}`);const c=await e.u62();i.setVarint(o,c)}else{if(i.bytes.has(o))throw new Error(`duplicate parameter id: ${o.toString()}`);const c=await e.u53(),u=await e.read(c);i.setBytes(o,u)}}return i}}const Ws=0x02n,Hs=0x04n,Js=0x08n,Xs=0x0en,Ks=0x10n,Ys=0x20n,Qs=0x22n,er=0x09n,tr=0x21n;class de{vars;bytes;constructor(){this.vars=new Map,this.bytes=new Map}get subscriberPriority(){const e=this.vars.get(Ys);return e!==void 0?Number(e):void 0}set subscriberPriority(e){this.vars.set(Ys,BigInt(e))}get groupOrder(){const e=this.vars.get(Qs);return e!==void 0?Number(e):void 0}set groupOrder(e){this.vars.set(Qs,BigInt(e))}get forward(){const e=this.vars.get(Ks);return e!==void 0?e!==0n:void 0}set forward(e){this.vars.set(Ks,e?1n:0n)}get publisherPriority(){const e=this.vars.get(Xs);return e!==void 0?Number(e):void 0}set publisherPriority(e){this.vars.set(Xs,BigInt(e))}get expires(){return this.vars.get(Js)}set expires(e){this.vars.set(Js,e)}get deliveryTimeout(){return this.vars.get(Ws)}set deliveryTimeout(e){this.vars.set(Ws,e)}get maxCacheDuration(){return this.vars.get(Hs)}set maxCacheDuration(e){this.vars.set(Hs,e)}get largest(){const e=this.bytes.get(er);if(!e||e.length===0)return;const[s,r]=ms(e),[i]=ms(r);return{groupId:BigInt(s),objectId:BigInt(i)}}set largest(e){const s=Vs(Number(e.groupId)),r=Vs(Number(e.objectId)),i=new Uint8Array(s.length+r.length);i.set(s,0),i.set(r,s.length),this.bytes.set(er,i)}get subscriptionFilter(){const e=this.bytes.get(tr);if(!(!e||e.length===0))return e[0]}set subscriptionFilter(e){this.bytes.set(tr,new Uint8Array([e]))}async encode(e,s){if(await e.u53(this.vars.size+this.bytes.size),s===m.DRAFT_16){const r=[];for(const n of this.vars.keys())r.push({key:n,isVar:!0});for(const n of this.bytes.keys())r.push({key:n,isVar:!1});r.sort((n,a)=>n.key<a.key?-1:n.key>a.key?1:0);let i=0n;for(let n=0;n<r.length;n++){const{key:a,isVar:o}=r[n],c=n===0?a:a-i;if(i=a,await e.u62(c),o)await e.u62(this.vars.get(a));else{const u=this.bytes.get(a);await e.u53(u.length),await e.write(u)}}}else{for(const[r,i]of this.vars)await e.u62(r),await e.u62(i);for(const[r,i]of this.bytes)await e.u62(r),await e.u53(i.length),await e.write(i)}}static async decode(e,s){const r=await e.u53(),i=new de;let n=0n;for(let a=0;a<r;a++){let o;if(s===m.DRAFT_16){const c=await e.u62();o=a===0?c:n+c,n=o}else o=await e.u62();if(o%2n===0n){if(i.vars.has(o))throw new Error(`duplicate message parameter id: ${o.toString()}`);const c=await e.u62();i.vars.set(o,c)}else{if(i.bytes.has(o))throw new Error(`duplicate message parameter id: ${o.toString()}`);const c=await e.u53(),u=await e.read(c);i.bytes.set(o,u)}}return i}}class ne{static id=29;requestId;trackNamespace;trackName;trackAlias;groupOrder;contentExists;largest;forward;constructor({requestId:e,trackNamespace:s,trackName:r,trackAlias:i,groupOrder:n,contentExists:a,largest:o,forward:c}){this.requestId=e,this.trackNamespace=s,this.trackName=r,this.trackAlias=i,this.groupOrder=n,this.contentExists=a,this.largest=o,this.forward=c}async#e(e,s){if(await e.u62(this.requestId),await we(e,this.trackNamespace),await e.string(this.trackName),await e.u62(this.trackAlias),s===m.DRAFT_15||s===m.DRAFT_16){const r=new de;r.groupOrder=this.groupOrder,r.forward=this.forward,this.largest&&(r.largest=this.largest),await r.encode(e,s)}else if(s===m.DRAFT_14){if(await e.u8(this.groupOrder),await e.bool(this.contentExists),this.contentExists!==!!this.largest)throw new Error("contentExists and largest must both be true or false");this.largest&&(await e.u62(this.largest.groupId),await e.u62(this.largest.objectId)),await e.bool(this.forward),await e.u53(0)}else S(s)}async encode(e,s){return _(e,r=>this.#e(r,s))}static async decode(e,s){return k(e,r=>ne.#t(r,s))}static async#t(e,s){const r=await e.u62(),i=await me(e),n=await e.string(),a=await e.u62();if(s===m.DRAFT_15||s===m.DRAFT_16){const o=await de.decode(e,s),c=o.groupOrder??2,u=o.forward??!0,d=o.largest;return new ne({requestId:r,trackNamespace:i,trackName:n,trackAlias:a,groupOrder:c,contentExists:!!d,largest:d,forward:u})}else if(s===m.DRAFT_14){const o=await e.u8(),c=await e.bool(),u=c?{groupId:await e.u62(),objectId:await e.u62()}:void 0,d=await e.bool();return await j.decode(e,s),new ne({requestId:r,trackNamespace:i,trackName:n,trackAlias:a,groupOrder:o,contentExists:c,largest:u,forward:d})}else S(s)}}class Tt{static id=30;async#e(e){throw new Error("PUBLISH_OK messages are not supported")}async encode(e,s){return _(e,this.#e.bind(this))}static async decode(e,s){return k(e,Tt.#t)}static async#t(e){throw new Error("PUBLISH_OK messages are not supported")}}class We{static id=31;requestId;errorCode;reasonPhrase;constructor({requestId:e,errorCode:s,reasonPhrase:r}){this.requestId=e,this.errorCode=s,this.reasonPhrase=r}async#e(e){await e.u62(this.requestId),await e.u62(BigInt(this.errorCode)),await e.string(this.reasonPhrase)}async encode(e,s){return _(e,this.#e.bind(this))}static async decode(e,s){return k(e,We.#t)}static async#t(e){const s=await e.u62(),r=Number(await e.u62()),i=await e.string();return new We({requestId:s,errorCode:r,reasonPhrase:i})}}class ee{static id=11;requestId;statusCode;reasonPhrase;constructor({requestId:e,statusCode:s,reasonPhrase:r}){this.requestId=e,this.statusCode=s,this.reasonPhrase=r}async#e(e){await e.u62(this.requestId),await e.u62(BigInt(this.statusCode)),await e.u62(BigInt(0)),await e.string(this.reasonPhrase)}async encode(e,s){return _(e,this.#e.bind(this))}static async decode(e,s){return k(e,ee.#t)}static async#t(e){const s=await e.u62(),r=Number(await e.u62());await e.u62();const i=await e.string();return new ee({requestId:s,statusCode:r,reasonPhrase:i})}}class ae{static id=6;requestId;trackNamespace;constructor({requestId:e,trackNamespace:s}){this.requestId=e,this.trackNamespace=s}async#e(e,s){await e.u62(this.requestId),await we(e,this.trackNamespace),await e.u53(0)}async encode(e,s){return _(e,r=>this.#e(r,s))}static async decode(e,s){return k(e,r=>ae.#t(r,s))}static async#t(e,s){const r=await e.u62(),i=await me(e);return await j.decode(e,s),new ae({requestId:r,trackNamespace:i})}}class ct{static id=7;requestId;constructor({requestId:e}){this.requestId=e}async#e(e){await e.u62(this.requestId)}async encode(e,s){return _(e,this.#e.bind(this))}static async decode(e,s){return k(e,ct.#t)}static async#t(e){const s=await e.u62();return new ct({requestId:s})}}class ut{static id=8;requestId;errorCode;reasonPhrase;constructor({requestId:e,errorCode:s,reasonPhrase:r}){this.requestId=e,this.errorCode=s,this.reasonPhrase=r}async#e(e){await e.u62(this.requestId),await e.u62(BigInt(this.errorCode)),await e.string(this.reasonPhrase)}async encode(e,s){return _(e,this.#e.bind(this))}static async decode(e,s){return k(e,ut.#t)}static async#t(e){const s=await e.u62(),r=Number(await e.u62()),i=await e.string();return new ut({requestId:s,errorCode:r,reasonPhrase:i})}}class le{static id=12;trackNamespace;requestId;errorCode;reasonPhrase;constructor({trackNamespace:e="",errorCode:s=0,reasonPhrase:r="",requestId:i=0n}={}){this.trackNamespace=e,this.requestId=i,this.errorCode=s,this.reasonPhrase=r}async#e(e,s){s===m.DRAFT_16?await e.u62(this.requestId):await we(e,this.trackNamespace),await e.u62(BigInt(this.errorCode)),await e.string(this.reasonPhrase)}async encode(e,s){return _(e,r=>this.#e(r,s))}static async decode(e,s){return k(e,r=>le.#t(r,s))}static async#t(e,s){let r="",i=0n;s===m.DRAFT_16?i=await e.u62():r=await me(e);const n=Number(await e.u62()),a=await e.string();return new le({trackNamespace:r,errorCode:n,reasonPhrase:a,requestId:i})}}class X{static id=9;trackNamespace;requestId;constructor({trackNamespace:e="",requestId:s=0n}={}){this.trackNamespace=e,this.requestId=s}async#e(e,s){s===m.DRAFT_16?await e.u62(this.requestId):await we(e,this.trackNamespace)}async encode(e,s){return _(e,r=>this.#e(r,s))}static async decode(e,s){return k(e,r=>X.#t(r,s))}static async#t(e,s){if(s===m.DRAFT_16){const i=await e.u62();return new X({requestId:i})}const r=await me(e);return new X({trackNamespace:r})}}class he{static id=21;requestId;constructor({requestId:e}){this.requestId=e}async#e(e){await e.u62(this.requestId)}async encode(e,s){return _(e,this.#e.bind(this))}static async#t(e){return new he({requestId:await e.u62()})}static async decode(e,s){return k(e,he.#t)}}class fe{static id=26;requestId;constructor({requestId:e}){this.requestId=e}async#e(e){await e.u62(this.requestId)}async encode(e,s){return _(e,this.#e.bind(this))}static async#t(e){return new fe({requestId:await e.u62()})}static async decode(e,s){return k(e,fe.#t)}}class te{static id=7;requestId;parameters;constructor({requestId:e,parameters:s=new de}){this.requestId=e,this.parameters=s}async#e(e,s){await e.u62(this.requestId),await this.parameters.encode(e,s)}async encode(e,s){return _(e,r=>this.#e(r,s))}static async#t(e,s){const r=await e.u62(),i=await de.decode(e,s);return new te({requestId:r,parameters:i})}static async decode(e,s){return k(e,r=>te.#t(r,s))}}class K{static id=5;requestId;errorCode;reasonPhrase;retryInterval;constructor({requestId:e,errorCode:s,reasonPhrase:r,retryInterval:i=0n}){this.requestId=e,this.errorCode=s,this.reasonPhrase=r,this.retryInterval=i}async#e(e,s){await e.u62(this.requestId),await e.u62(BigInt(this.errorCode)),s===m.DRAFT_16&&await e.u62(this.retryInterval),await e.string(this.reasonPhrase)}async encode(e,s){return _(e,r=>this.#e(r,s))}static async#t(e,s){const r=await e.u62(),i=Number(await e.u62()),n=s===m.DRAFT_16?await e.u62():0n,a=await e.string();return new K({requestId:r,errorCode:i,reasonPhrase:a,retryInterval:n})}static async decode(e,s){return k(e,r=>K.#t(r,s))}}const Dn=128;class Y{static id=32;versions;parameters;constructor({versions:e,parameters:s=new j}){this.versions=e,this.parameters=s}async#e(e,s){if(s===m.DRAFT_15||s===m.DRAFT_16)await this.parameters.encode(e,s);else if(s===m.DRAFT_14){await e.u53(this.versions.length);for(const r of this.versions)await e.u53(r);await this.parameters.encode(e,s)}else S(s)}async encode(e,s){return _(e,r=>this.#e(r,s))}static async#t(e,s){if(s===m.DRAFT_15||s===m.DRAFT_16){const r=await j.decode(e,s);return new Y({versions:[s],parameters:r})}else if(s===m.DRAFT_14){const r=await e.u53();if(r>Dn)throw new Error(`too many versions: ${r}`);const i=[];for(let a=0;a<r;a++){const o=await e.u53();i.push(o)}const n=await j.decode(e,s);return new Y({versions:i,parameters:n})}else S(s)}static async decode(e,s){return k(e,r=>Y.#t(r,s))}}class Q{static id=33;version;parameters;constructor({version:e,parameters:s=new j}){this.version=e,this.parameters=s}async#e(e,s){s===m.DRAFT_15||s===m.DRAFT_16?await this.parameters.encode(e,s):s===m.DRAFT_14?(await e.u53(this.version),await this.parameters.encode(e,s)):S(s)}async encode(e,s){return _(e,r=>this.#e(r,s))}static async#t(e,s){if(s===m.DRAFT_15||s===m.DRAFT_16){const r=await j.decode(e,s);return new Q({version:s,parameters:r})}else if(s===m.DRAFT_14){const r=await e.u53(),i=await j.decode(e,s);return new Q({version:r,parameters:i})}else S(s)}static async decode(e,s){return k(e,r=>Q.#t(r,s))}}const De=2;let Pe=class Mt{static id=3;requestId;trackNamespace;trackName;subscriberPriority;constructor({requestId:e,trackNamespace:s,trackName:r,subscriberPriority:i}){this.requestId=e,this.trackNamespace=s,this.trackName=r,this.subscriberPriority=i}async#e(e,s){if(await e.u62(this.requestId),await we(e,this.trackNamespace),await e.string(this.trackName),s===m.DRAFT_15||s===m.DRAFT_16){const r=new de;r.subscriberPriority=this.subscriberPriority,r.groupOrder=De,r.forward=!0,r.subscriptionFilter=2,await r.encode(e,s)}else s===m.DRAFT_14?(await e.u8(this.subscriberPriority),await e.u8(De),await e.bool(!0),await e.u53(2),await e.u53(0)):S(s)}async encode(e,s){return _(e,r=>this.#e(r,s))}static async decode(e,s){return k(e,r=>Mt.#t(r,s))}static async#t(e,s){const r=await e.u62(),i=await me(e),n=await e.string();if(s===m.DRAFT_15||s===m.DRAFT_16){const a=await de.decode(e,s),o=a.subscriberPriority??128;let c=a.groupOrder??De;if(c>2)throw new Error(`unknown group order: ${c}`);c===0&&(c=De);const u=a.forward??!0;if(!u)throw new Error(`unsupported forward value: ${u}`);const d=a.subscriptionFilter??2;if(d!==1&&d!==2)throw new Error(`unsupported filter type: ${d}`);return new Mt({requestId:r,trackNamespace:i,trackName:n,subscriberPriority:o})}else if(s===m.DRAFT_14){const a=await e.u8();let o=await e.u8();if(o>2)throw new Error(`unknown group order: ${o}`);o===0&&(o=De);const c=await e.bool();if(!c)throw new Error(`unsupported forward value: ${c}`);const u=await e.u53();if(u!==1&&u!==2)throw new Error(`unsupported filter type: ${u}`);return await j.decode(e,s),new Mt({requestId:r,trackNamespace:i,trackName:n,subscriberPriority:a})}else S(s)}},Oe=class gs{static id=4;requestId;trackAlias;constructor({requestId:e,trackAlias:s}){this.requestId=e,this.trackAlias=s}async#e(e,s){if(await e.u62(this.requestId),await e.u62(this.trackAlias),s===m.DRAFT_15||s===m.DRAFT_16){const r=new de;r.groupOrder=De,await r.encode(e,s)}else s===m.DRAFT_14?(await e.u62(0n),await e.u8(De),await e.bool(!1),await e.u53(0)):S(s)}async encode(e,s){return _(e,r=>this.#e(r,s))}static async decode(e,s){return k(e,r=>gs.#t(r,s))}static async#t(e,s){const r=await e.u62(),i=await e.u62();if(s===m.DRAFT_15||s===m.DRAFT_16)await de.decode(e,s);else if(s===m.DRAFT_14){const n=await e.u62();if(n!==BigInt(0))throw new Error(`unsupported expires: ${n}`);await e.u8(),await e.bool()&&(await e.u62(),await e.u62()),await j.decode(e,s)}else S(s);return new gs({requestId:r,trackAlias:i})}};class He{static id=5;requestId;errorCode;reasonPhrase;constructor({requestId:e,errorCode:s,reasonPhrase:r}){this.requestId=e,this.errorCode=s,this.reasonPhrase=r}async#e(e){await e.u62(this.requestId),await e.u62(BigInt(this.errorCode)),await e.string(this.reasonPhrase)}async encode(e,s){return _(e,this.#e.bind(this))}static async decode(e,s){return k(e,He.#t)}static async#t(e){const s=await e.u62(),r=Number(await e.u62()),i=await e.string();return new He({requestId:s,errorCode:r,reasonPhrase:i})}}class oe{static id=10;requestId;constructor({requestId:e}){this.requestId=e}async#e(e){await e.u62(this.requestId)}async encode(e,s){return _(e,this.#e.bind(this))}static async decode(e,s){return k(e,oe.#t)}static async#t(e){const s=await e.u62();return new oe({requestId:s})}}class H{static id=17;namespace;requestId;subscribeOptions;constructor({namespace:e,requestId:s,subscribeOptions:r=1}){this.namespace=e,this.requestId=s,this.subscribeOptions=r}async#e(e,s){await e.u62(this.requestId),await we(e,this.namespace),s===m.DRAFT_16&&await e.u53(this.subscribeOptions),await e.u53(0)}async encode(e,s){return _(e,r=>this.#e(r,s))}static async decode(e,s){return k(e,r=>H.#t(r,s))}static async#t(e,s){const r=await e.u62(),i=await me(e);let n=1;return s===m.DRAFT_16&&(n=await e.u53()),await j.decode(e,s),new H({namespace:i,requestId:r,subscribeOptions:n})}}class dt{static id=18;requestId;constructor({requestId:e}){this.requestId=e}async#e(e){await e.u62(this.requestId)}async encode(e,s){return _(e,this.#e.bind(this))}static async decode(e,s){return k(e,dt.#t)}static async#t(e){const s=await e.u62();return new dt({requestId:s})}}class lt{static id=19;requestId;errorCode;reasonPhrase;constructor({requestId:e,errorCode:s,reasonPhrase:r}){this.requestId=e,this.errorCode=s,this.reasonPhrase=r}async#e(e){await e.u62(this.requestId),await e.u62(BigInt(this.errorCode)),await e.string(this.reasonPhrase)}async encode(e,s){return _(e,this.#e.bind(this))}static async decode(e,s){return k(e,lt.#t)}static async#t(e){const s=await e.u62(),r=Number(await e.u62()),i=await e.string();return new lt({requestId:s,errorCode:r,reasonPhrase:i})}}class _e{static id=20;requestId;constructor({requestId:e}){this.requestId=e}async#e(e){await e.u62(this.requestId)}async encode(e,s){return _(e,this.#e.bind(this))}static async decode(e,s){return k(e,_e.#t)}static async#t(e){const s=await e.u62();return new _e({requestId:s})}}class Je{static id=8;suffix;constructor({suffix:e}){this.suffix=e}async#e(e){await we(e,this.suffix)}async encode(e,s){return _(e,this.#e.bind(this))}static async decode(e,s){return k(e,Je.#t)}static async#t(e){const s=await me(e);return new Je({suffix:s})}}class Xe{static id=14;suffix;constructor({suffix:e}){this.suffix=e}async#e(e){await we(e,this.suffix)}async encode(e,s){return _(e,this.#e.bind(this))}static async decode(e,s){return k(e,Xe.#t)}static async#t(e){const s=await me(e);return new Xe({suffix:s})}}class pe{static id=13;trackNamespace;trackName;constructor({trackNamespace:e,trackName:s}){this.trackNamespace=e,this.trackName=s}async#e(e){await we(e,this.trackNamespace),await e.string(this.trackName)}async encode(e,s){return _(e,this.#e.bind(this))}static async decode(e,s){return k(e,pe.#t)}static async#t(e){const s=await me(e),r=await e.string();return new pe({trackNamespace:s,trackName:r})}}class Ne{static id=14;trackNamespace;trackName;statusCode;lastGroupId;lastObjectId;constructor({trackNamespace:e,trackName:s,statusCode:r,lastGroupId:i,lastObjectId:n}){this.trackNamespace=e,this.trackName=s,this.statusCode=r,this.lastGroupId=i,this.lastObjectId=n}async#e(e){await we(e,this.trackNamespace),await e.string(this.trackName),await e.u62(BigInt(this.statusCode)),await e.u62(this.lastGroupId),await e.u62(this.lastObjectId)}async encode(e,s){return _(e,this.#e.bind(this))}static async decode(e,s){return k(e,Ne.#t)}static async#t(e){const s=await me(e),r=await e.string(),i=Number(await e.u62()),n=await e.u62(),a=await e.u62();return new Ne({trackNamespace:s,trackName:r,statusCode:i,lastGroupId:n,lastObjectId:a})}static STATUS_IN_PROGRESS=0;static STATUS_NOT_FOUND=1;static STATUS_NOT_AUTHORIZED=2;static STATUS_ENDED=3}const Fn={[Y.id]:Y,[Q.id]:Q,[Pe.id]:Pe,[Oe.id]:Oe,[He.id]:He,[ae.id]:ae,[ct.id]:ct,[ut.id]:ut,[X.id]:X,[oe.id]:oe,[ee.id]:ee,[le.id]:le,[pe.id]:pe,[Ne.id]:Ne,[ue.id]:ue,[ge.id]:ge,[ve.id]:ve,[ye.id]:ye,[xt.id]:xt,[H.id]:H,[dt.id]:dt,[lt.id]:lt,[_e.id]:_e,[ne.id]:ne,[Tt.id]:Tt,[We.id]:We,[he.id]:he,[fe.id]:fe},Mn={[Y.id]:Y,[Q.id]:Q,[Pe.id]:Pe,[Oe.id]:Oe,[K.id]:K,[ae.id]:ae,[te.id]:te,[X.id]:X,[oe.id]:oe,[ee.id]:ee,[le.id]:le,[pe.id]:pe,[ue.id]:ue,[ge.id]:ge,[ve.id]:ve,[ye.id]:ye,[H.id]:H,[_e.id]:_e,[ne.id]:ne,[he.id]:he,[fe.id]:fe},Ln={[Y.id]:Y,[Q.id]:Q,[Pe.id]:Pe,[Oe.id]:Oe,[K.id]:K,[ae.id]:ae,[te.id]:te,[X.id]:X,[oe.id]:oe,[ee.id]:ee,[le.id]:le,[pe.id]:pe,[ue.id]:ue,[ge.id]:ge,[ve.id]:ve,[ye.id]:ye,[ne.id]:ne,[he.id]:he,[fe.id]:fe};class Bn{stream;version;#e=0n;#t;#s;#r;#i=new Zs;#n=new Zs;constructor({stream:e,maxRequestId:s,version:r=m.DRAFT_14}){this.stream=e,this.version=r,this.#t=s,this.#s=new Promise(i=>{this.#r=i})}async write(e){console.debug("message write",e),await this.#i.runExclusive(async()=>{await this.stream.writer.u53(e.constructor.id),await e.encode(this.stream.writer,this.version)})}async read(){return await this.#n.runExclusive(async()=>{const e=await this.stream.reader.u53(),s=this.version===m.DRAFT_16?Ln:this.version===m.DRAFT_15?Mn:Fn;if(!(e in s))throw new Error(`Unknown control message type: ${e}`);try{return await s[e].decode(this.stream.reader,this.version)}catch(r){throw console.error("failed to decode message",e,r),r}})}maxRequestId(e){if(e<=this.#t)throw new Error(`max request id must be greater than current max request id: max=${e} current=${this.#t}`);this.#t=e,this.#r(),this.#s=new Promise(s=>{this.#r=s})}async nextRequestId(){for(;;){const e=this.#e;if(e<this.#t)return this.#e+=2n,e;if(!this.#s)return;console.warn("blocked on max request id"),await this.#s}}close(){this.#r(),this.#s=void 0}}const sr=3;let oi=class ci{flags;trackAlias;groupId;subGroupId;publisherPriority;constructor({trackAlias:e,groupId:s,subGroupId:r,publisherPriority:i,flags:n}){this.flags=n,this.trackAlias=e,this.groupId=s,this.subGroupId=r,this.publisherPriority=i}async encode(e){if(!this.flags.hasSubgroup&&this.subGroupId!==0)throw new Error(`Subgroup ID must be 0 if hasSubgroup is false: ${this.subGroupId}`);let r=this.flags.hasPriority?16:48;this.flags.hasExtensions&&(r|=1),this.flags.hasSubgroupObject&&(r|=2),this.flags.hasSubgroup&&(r|=4),this.flags.hasEnd&&(r|=8),await e.u53(r),await e.u62(this.trackAlias),await e.u53(this.groupId),this.flags.hasSubgroup&&await e.u53(this.subGroupId),this.flags.hasPriority&&await e.u8(this.publisherPriority)}static async decode(e){const s=await e.u53();let r,i;if(s>=16&&s<=31)r=!0,i=s;else if(s>=48&&s<=63)r=!1,i=s-32;else throw new Error(`Unsupported group type: ${s}`);const n={hasExtensions:(i&1)!==0,hasSubgroupObject:(i&2)!==0,hasSubgroup:(i&4)!==0,hasEnd:(i&8)!==0,hasPriority:r},a=await e.u62(),o=await e.u53(),c=n.hasSubgroup?await e.u53():0,u=r?await e.u8():128;return new ci({trackAlias:a,groupId:o,subGroupId:c,publisherPriority:u,flags:n})}};class st{payload;constructor({payload:e}={}){this.payload=e}async encode(e,s){await e.u53(0),s.hasExtensions&&await e.u53(0),this.payload!==void 0?(await e.u53(this.payload.byteLength),this.payload.byteLength===0?await e.u53(0):await e.write(this.payload)):(await e.u53(0),await e.u53(sr))}static async decode(e,s){const r=await e.u53();if(r!==0)throw new Error(`object ID delta is not supported: ${r}`);if(s.hasExtensions){const a=await e.u53();await e.read(a)}const i=await e.u53();if(i>0){const a=await e.read(i);return new st({payload:a})}const n=await e.u53();if(s.hasEnd){if(n===0)return new st({payload:new Uint8Array(0)})}else if(n===0||n===sr)return new st;throw new Error(`Unsupported object status: ${n}`)}}let Vn=class{#e;#t;#s=new Map;#r=new Set;constructor({quic:e,control:s}){this.#e=e,this.#t=s}publish(e,s){this.#s.set(e,s),this.#o(e,!0),this.#i(e,s)}async#i(e,s){try{const r=await this.#t.nextRequestId();if(r===void 0)return;const i=new ae({requestId:r,trackNamespace:e});await this.#t.write(i),await s.closed;const n=new X({trackNamespace:e});await this.#t.write(n)}catch(r){const i=B(r);console.warn(`announce failed: broadcast=${e} error=${i.message}`)}finally{s.close(),this.#s.delete(e),this.#o(e,!1)}}async handleSubscribe(e){const s=e.trackNamespace,r=this.#s.get(s);if(!r){if(this.#t.version===m.DRAFT_15||this.#t.version===m.DRAFT_16){const a=new K({requestId:e.requestId,errorCode:404,reasonPhrase:"Broadcast not found"});await this.#t.write(a)}else if(this.#t.version===m.DRAFT_14){const a=new He({requestId:e.requestId,errorCode:404,reasonPhrase:"Broadcast not found"});await this.#t.write(a)}else S(this.#t.version);return}const i=r.subscribe(e.trackName,e.subscriberPriority),n=new Oe({requestId:e.requestId,trackAlias:e.requestId});await this.#t.write(n),console.debug(`publish ok: broadcast=${s} track=${i.name}`),this.#n(e.requestId,s,i)}async#n(e,s,r){try{for(;;){const n=await r.nextGroup();if(!n)break;this.#a(e,n)}console.debug(`publish done: broadcast=${s} track=${r.name}`);const i=new ee({requestId:e,statusCode:200,reasonPhrase:"OK"});await this.#t.write(i)}catch(i){const n=B(i);console.warn(`publish error: broadcast=${s} track=${r.name} error=${n.message}`);const a=new ee({requestId:e,statusCode:500,reasonPhrase:n.message});await this.#t.write(a)}finally{r.close()}}async#a(e,s){try{const r=await Ke.open(this.#e),i=new oi({trackAlias:e,groupId:s.sequence,subGroupId:0,publisherPriority:0,flags:{hasExtensions:!1,hasSubgroup:!1,hasSubgroupObject:!1,hasEnd:!0,hasPriority:!0}});console.debug("sending group header",i),await i.encode(r);try{for(;;){const n=await Promise.race([s.readFrame(),r.closed]);if(!n)break;await new st({payload:n}).encode(r,i.flags)}r.close()}catch(n){r.reset(B(n))}}finally{s.close()}}async handleTrackStatusRequest(e){const s=new Ne({trackNamespace:e.trackNamespace,trackName:e.trackName,statusCode:Ne.STATUS_NOT_FOUND,lastGroupId:0n,lastObjectId:0n});await this.#t.write(s)}async handleUnsubscribe(e){}async handlePublishNamespaceOk(e){}async handlePublishNamespaceError(e){}async handlePublishNamespaceCancel(e){}async handleSubscribeNamespace(e){}async handleUnsubscribeNamespace(e){}async handleRequestOk(e){}async handleRequestError(e){}async handleSubscribeNamespaceStream(e){const s=this.#t.version;try{const r=await H.decode(e.reader,s),i=r.namespace;console.debug(`subscribe_namespace stream: prefix=${i}`),await e.writer.u53(te.id),await new te({requestId:r.requestId}).encode(e.writer,s);const a=new xs(i);for(const o of this.#s.keys()){const c=Lt(i,o);c!==null&&a.append({path:c,active:!0})}this.#r.add(a),e.reader.closed.then(()=>a.close(),()=>a.close());try{for(;;){const o=await a.next();if(!o)break;o.active?(console.debug(`namespace: suffix=${o.path} active=true`),await e.writer.u53(Je.id),await new Je({suffix:o.path}).encode(e.writer,s)):(console.debug(`namespace: suffix=${o.path} active=false`),await e.writer.u53(Xe.id),await new Xe({suffix:o.path}).encode(e.writer,s))}}finally{a.close(),this.#r.delete(a)}e.close()}catch(r){const i=B(r);console.debug(`subscribe_namespace stream error: ${i.message}`),e.abort(i)}}#o(e,s){for(const r of this.#r){const i=Lt(r.prefix,e);if(i!==null)try{r.append({path:i,active:s})}catch{}}}},jn=class{#e;#t=new Set;#s=new Set;#r=new Map;#i=new Map;#n=new Map;#a;constructor({control:e,quic:s}){this.#e=e,this.#a=s}announced(e=wt()){const s=new xs(e);for(const r of this.#t)r.startsWith(e)&&s.append({path:r,active:!0});return this.#s.add(s),this.#o(s,e).finally(()=>{this.#s.delete(s)}),s}async#o(e,s){this.#e.version===m.DRAFT_16?await this.#d(e,s):await this.#c(e,s)}async#c(e,s){const r=await this.#e.nextRequestId();if(r!==void 0)try{this.#e.write(new H({namespace:s,requestId:r})),await e.closed}finally{this.#e.write(new _e({requestId:r}))}}async#d(e,s){const r=await this.#e.nextRequestId();if(r===void 0)return;const i=this.#e.version;try{const n=await ot.open(this.#a);await n.writer.u53(H.id),await new H({namespace:s,requestId:r}).encode(n.writer,i);const o=await n.reader.u53();if(o===te.id)await te.decode(n.reader,i);else if(o===K.id){const u=await K.decode(n.reader,i);throw new Error(`SUBSCRIBE_NAMESPACE error: code=${u.errorCode} reason=${u.reasonPhrase}`)}else throw new Error(`unexpected response type: ${o}`);const c=(async()=>{for(;!await n.reader.done();){const d=await n.reader.u53();if(d===Je.id){const h=await Je.decode(n.reader,i),p=Bt(s,h.suffix);console.debug(`announced: broadcast=${p} active=true`),this.#t.add(p);for(const w of this.#s)w.append({path:p,active:!0})}else if(d===Xe.id){const h=await Xe.decode(n.reader,i),p=Bt(s,h.suffix);console.debug(`announced: broadcast=${p} active=false`),this.#t.delete(p);for(const w of this.#s)w.append({path:p,active:!1})}else throw new Error(`unexpected message type on subscribe_namespace stream: ${d}`)}})();await Promise.race([c,e.closed]),n.close()}catch(n){const a=B(n);console.warn(`subscribe_namespace error: ${a.message}`)}}consume(e){const s=new Qr;return(async()=>{for(;;){const r=await s.requested();if(!r)break;this.#l(e,r)}})(),s}async#l(e,s){const r=await this.#e.nextRequestId();if(r===void 0)return;this.#r.set(r,s.track),console.debug(`subscribe start: id=${r} broadcast=${e} track=${s.track.name}`);const i=new Pe({requestId:r,trackNamespace:e,trackName:s.track.name,subscriberPriority:s.priority}),n=new Promise((a,o)=>{this.#n.set(r,{resolve:a,reject:o})});await this.#e.write(i);try{const a=await n;this.#i.set(a.trackAlias,r),console.debug(`subscribe ok: id=${r} broadcast=${e} track=${s.track.name}`);try{await s.track.closed;const o=new oe({requestId:r});await this.#e.write(o),console.debug(`unsubscribe: id=${r} broadcast=${e} track=${s.track.name}`)}finally{this.#i.delete(a.trackAlias)}}catch(a){const o=B(a);s.track.close(o),console.warn(`subscribe error: id=${r} broadcast=${e} track=${s.track.name} error=${o.message}`)}finally{this.#r.delete(r),this.#n.delete(r)}}async handleSubscribeOk(e){const s=this.#n.get(e.requestId);s?s.resolve(e):console.warn("handleSubscribeOk unknown requestId",e.requestId)}async handleSubscribeError(e){const s=this.#n.get(e.requestId);s?s.reject(new Error(`SUBSCRIBE_ERROR: code=${e.errorCode} reason=${e.reasonPhrase}`)):console.warn("handleSubscribeError unknown requestId",e.requestId)}async handleGroup(e,s){const r=new Ts(e.groupId);if(e.subGroupId!==0)throw new Error("subgroups are not supported");try{let i=this.#i.get(e.trackAlias);i===void 0&&(i=e.trackAlias,console.warn("unknown track alias, using request ID"));const n=this.#r.get(i);if(!n)throw new Error(`unknown track: trackAlias=${e.trackAlias} requestId=${this.#i.get(e.trackAlias)}`);for(n.writeGroup(r);await Promise.race([s.done(),r.closed,n.closed])===!1;){const o=await st.decode(s,e.flags);if(o.payload===void 0)break;r.writeFrame(o.payload)}r.close()}catch(i){const n=B(i);r.close(n),s.stop(n)}}async handlePublish(e){if(this.#e.version===m.DRAFT_15||this.#e.version===m.DRAFT_16){const s=new K({requestId:e.requestId,errorCode:500,reasonPhrase:"publish not supported"});await this.#e.write(s)}else if(this.#e.version===m.DRAFT_14){const s=new We({requestId:e.requestId,errorCode:500,reasonPhrase:"publish not supported"});await this.#e.write(s)}else S(this.#e.version)}async handlePublishDone(e){const s=this.#n.get(e.requestId);s&&s.reject(new Error(`PUBLISH_DONE: code=${e.statusCode} reason=${e.reasonPhrase}`))}async handlePublishNamespace(e){if(this.#t.has(e.trackNamespace)){console.warn("duplicate PUBLISH_NAMESPACE message");return}this.#t.add(e.trackNamespace),console.debug(`announced: broadcast=${e.trackNamespace} active=true`);for(const s of this.#s)s.append({path:e.trackNamespace,active:!0})}async handlePublishNamespaceDone(e){if(!this.#t.has(e.trackNamespace)){console.warn("unknown PUBLISH_NAMESPACE_DONE message");return}this.#t.delete(e.trackNamespace),console.debug(`announced: broadcast=${e.trackNamespace} active=false`);for(const s of this.#s)s.append({path:e.trackNamespace,active:!1})}async handleSubscribeNamespaceOk(e){}async handleSubscribeNamespaceError(e){throw new Error("SUBSCRIBE_NAMESPACE_ERROR messages are not supported")}async handleTrackStatus(e){throw new Error("TRACK_STATUS messages are not supported")}async handleRequestOk(e){console.debug("received REQUEST_OK",e.requestId)}async handleRequestError(e){const s=this.#n.get(e.requestId);s?s.reject(new Error(`REQUEST_ERROR: code=${e.errorCode} reason=${e.reasonPhrase}`)):console.warn("handleRequestError unknown requestId",e.requestId)}},Gn=class{url;#e;#t;#s;#r;#i=!1;constructor({url:e,quic:s,control:r,maxRequestId:i,version:n}){this.url=e,this.#e=s,this.#t=new Bn({stream:r,maxRequestId:i,version:n}),this.#e.closed.finally(()=>{this.#t.close()}),this.#s=new Vn({quic:this.#e,control:this.#t}),this.#r=new jn({control:this.#t,quic:this.#e}),this.#n()}close(){if(!this.#i){this.#i=!0;try{this.#e.close()}catch{}}}async#n(){const e=[this.#a(),this.#l()];this.#t.version===m.DRAFT_16&&e.push(this.#h());try{await Promise.all(e)}catch(s){this.#i||console.error("fatal error running connection",s)}finally{this.close()}}publish(e,s){this.#s.publish(e,s)}announced(e=wt()){return this.#r.announced(e)}consume(e){return this.#r.consume(e)}async#a(){for(;;)try{const e=await this.#t.read();if(e instanceof Pe)await this.#s.handleSubscribe(e);else if(e instanceof oe)await this.#s.handleUnsubscribe(e);else if(e instanceof pe)await this.#s.handleTrackStatusRequest(e);else if(e instanceof ct)await this.#s.handlePublishNamespaceOk(e);else if(e instanceof ut)await this.#s.handlePublishNamespaceError(e);else if(e instanceof le)await this.#s.handlePublishNamespaceCancel(e);else if(e instanceof ae)await this.#r.handlePublishNamespace(e);else if(e instanceof X)await this.#r.handlePublishNamespaceDone(e);else if(e instanceof Oe)await this.#r.handleSubscribeOk(e);else if(e instanceof He)await this.#r.handleSubscribeError(e);else if(e instanceof ee)await this.#r.handlePublishDone(e);else if(e instanceof Ne)await this.#r.handleTrackStatus(e);else if(e instanceof ue)await this.#o(e);else if(e instanceof Y)await this.#c(e);else if(e instanceof Q)await this.#d(e);else if(e instanceof H)await this.#s.handleSubscribeNamespace(e);else if(e instanceof dt)await this.#r.handleSubscribeNamespaceOk(e);else if(e instanceof lt)await this.#r.handleSubscribeNamespaceError(e);else if(e instanceof _e)await this.#s.handleUnsubscribeNamespace(e);else if(e instanceof ne)await this.#r.handlePublish(e);else{if(e instanceof Tt)throw new Error("PUBLISH_OK messages are not supported");if(e instanceof We)throw new Error("PUBLISH_ERROR messages are not supported");if(e instanceof ge)throw new Error("FETCH messages are not supported");if(e instanceof ye)throw new Error("FETCH_OK messages are not supported");if(e instanceof xt)throw new Error("FETCH_ERROR messages are not supported");if(e instanceof ve)throw new Error("FETCH_CANCEL messages are not supported");e instanceof he?this.#t.maxRequestId(e.requestId):e instanceof fe?console.warn("ignoring REQUESTS_BLOCKED message"):e instanceof te?(await this.#s.handleRequestOk(e),await this.#r.handleRequestOk(e)):e instanceof K?(await this.#s.handleRequestError(e),await this.#r.handleRequestError(e)):S(e)}}catch(e){console.error("error processing control message",e);break}console.warn("control stream closed")}async#o(e){console.warn(`MOQLITE_INCOMPATIBLE: Received GOAWAY with redirect URI: ${e.newSessionUri}`),this.close()}async#c(e){console.error("Unexpected CLIENT_SETUP message received after connection established"),this.close()}async#d(e){console.error("Unexpected SERVER_SETUP message received after connection established"),this.close()}async#l(){const e=new ni(this.#e);for(;;){const s=await e.next();if(!s)break;this.#u(s).then(()=>{s.stop(new Error("cancel"))}).catch(r=>{console.error("error processing object stream",r),s.stop(r)})}}async#u(e){const s=await oi.decode(e);await this.#r.handleGroup(s,e)}async#h(){for(;;){const e=await ot.accept(this.#e);if(!e)break;this.#f(e).catch(s=>{console.error("error processing bidi stream",s),e.abort(new Error("bidi stream error"))})}}async#f(e){const s=await e.reader.u53();s===H.id?await this.#s.handleSubscribeNamespaceStream(e):(console.warn(`unexpected bidi stream type: ${s}`),e.abort(new Error("unexpected stream type")))}get closed(){return this.#e.closed.then(()=>{})}};async function be(t,e){let s=new Uint8Array;const r=new Ke(new WritableStream({write(i){const n=s.byteLength+i.byteLength;if(n>s.buffer.byteLength){const a=Math.max(n,s.buffer.byteLength*2),o=new ArrayBuffer(a),c=new Uint8Array(o,0,n);c.set(s),c.set(i,s.byteLength),s=c}else s=new Uint8Array(s.buffer,0,n),s.set(i,n-i.byteLength)}}));await e(r),r.close(),await r.closed,await t.u53(s.byteLength),await t.write(s)}async function ce(t,e){const s=await t.u53(),r=await t.read(s),i=new Yt(void 0,r),n=await e(i);if(!await i.done())throw new Error("Message decoding consumed too few bytes");return n}async function zt(t,e){if(!await t.done())return await ce(t,e)}const v={DRAFT_01:4279086337,DRAFT_02:4279086338,DRAFT_03:4279086339},ui="moql",di="moq-lite-03";class Te{suffix;active;hops;constructor(e){this.suffix=e.suffix,this.active=e.active,this.hops=e.hops??0}async#e(e,s){switch(await e.bool(this.active),await e.string(this.suffix),s){case v.DRAFT_03:await e.u53(this.hops);break;case v.DRAFT_01:case v.DRAFT_02:break;default:S(s)}}static async#t(e,s){const r=await e.bool(),i=Ze(await e.string());let n=0;switch(s){case v.DRAFT_03:n=await e.u53();break;case v.DRAFT_01:case v.DRAFT_02:break;default:S(s)}return new Te({suffix:i,active:r,hops:n})}async encode(e,s){return be(e,r=>this.#e(r,s))}static async decode(e,s){return ce(e,r=>Te.#t(r,s))}static async decodeMaybe(e,s){return zt(e,r=>Te.#t(r,s))}}class Rt{prefix;constructor(e){this.prefix=e}async#e(e){await e.string(this.prefix)}static async#t(e){const s=Ze(await e.string());return new Rt(s)}async encode(e){return be(e,this.#e.bind(this))}static async decode(e){return ce(e,Rt.#t)}}class Me{suffixes;constructor(e){this.suffixes=e}static#e(e){switch(e){case v.DRAFT_01:case v.DRAFT_02:break;case v.DRAFT_03:throw new Error("announce init not supported for Draft03");default:S(e)}}async#t(e){await e.u53(this.suffixes.length);for(const s of this.suffixes)await e.string(s)}static async#s(e){const s=await e.u53(),r=[];for(let i=0;i<s;i++)r.push(Ze(await e.string()));return new Me(r)}async encode(e,s){return Me.#e(s),be(e,this.#t.bind(this))}static async decode(e,s){return Me.#e(s),ce(e,Me.#s)}}class rt{subscribe;sequence;constructor(e,s){this.subscribe=e,this.sequence=s}async#e(e){await e.u62(this.subscribe),await e.u53(this.sequence)}static async#t(e){return new rt(await e.u62(),await e.u53())}async encode(e){return be(e,this.#e.bind(this))}static async decode(e){return ce(e,rt.#t)}static async decodeMaybe(e){return zt(e,rt.#t)}}function is(t){switch(t){case v.DRAFT_03:break;case v.DRAFT_01:case v.DRAFT_02:throw new Error("probe not supported for this version");default:S(t)}}class kt{bitrate;constructor(e){this.bitrate=e}async#e(e){await e.u53(this.bitrate)}static async#t(e){return new kt(await e.u53())}async encode(e,s){return is(s),be(e,this.#e.bind(this))}static async decode(e,s){return is(s),ce(e,kt.#t)}static async decodeMaybe(e,s){return is(s),zt(e,kt.#t)}}class Le{priority;ordered;maxLatency;startGroup;endGroup;constructor(e){this.priority=e.priority,this.ordered=e.ordered??!0,this.maxLatency=e.maxLatency??0,this.startGroup=e.startGroup,this.endGroup=e.endGroup}async#e(e,s){switch(s){case v.DRAFT_03:await e.u8(this.priority),await e.bool(this.ordered),await e.u53(this.maxLatency),await e.u53(this.startGroup!==void 0?this.startGroup+1:0),await e.u53(this.endGroup!==void 0?this.endGroup+1:0);break;case v.DRAFT_01:case v.DRAFT_02:await e.u8(this.priority);break;default:S(s)}}static async#t(e,s){switch(s){case v.DRAFT_03:{const r=await e.u8(),i=await e.bool(),n=await e.u53(),a=await e.u53(),o=await e.u53();return new Le({priority:r,ordered:i,maxLatency:n,startGroup:a>0?a-1:void 0,endGroup:o>0?o-1:void 0})}case v.DRAFT_01:case v.DRAFT_02:return new Le({priority:await e.u8()});default:S(s)}}async encode(e,s){return be(e,r=>this.#e(r,s))}static async decode(e,s){return ce(e,r=>Le.#t(r,s))}static async decodeMaybe(e,s){return zt(e,r=>Le.#t(r,s))}}class it{id;broadcast;track;priority;ordered;maxLatency;startGroup;endGroup;constructor(e){this.id=e.id,this.broadcast=e.broadcast,this.track=e.track,this.priority=e.priority,this.ordered=e.ordered??!1,this.maxLatency=e.maxLatency??0,this.startGroup=e.startGroup,this.endGroup=e.endGroup}async#e(e,s){switch(await e.u62(this.id),await e.string(this.broadcast),await e.string(this.track),await e.u8(this.priority),s){case v.DRAFT_03:await e.bool(this.ordered),await e.u53(this.maxLatency),await e.u53(this.startGroup!==void 0?this.startGroup+1:0),await e.u53(this.endGroup!==void 0?this.endGroup+1:0);break;case v.DRAFT_01:case v.DRAFT_02:break;default:S(s)}}static async#t(e,s){const r=await e.u62(),i=Ze(await e.string()),n=await e.string(),a=await e.u8();switch(s){case v.DRAFT_03:{const o=await e.bool(),c=await e.u53(),u=await e.u53(),d=await e.u53();return new it({id:r,broadcast:i,track:n,priority:a,ordered:o,maxLatency:c,startGroup:u>0?u-1:void 0,endGroup:d>0?d-1:void 0})}case v.DRAFT_01:case v.DRAFT_02:return new it({id:r,broadcast:i,track:n,priority:a});default:S(s)}}async encode(e,s){return be(e,r=>this.#e(r,s))}static async decode(e,s){return ce(e,r=>it.#t(r,s))}}class Ve{priority;ordered;maxLatency;startGroup;endGroup;constructor({priority:e=0,ordered:s=!0,maxLatency:r=0,startGroup:i=void 0,endGroup:n=void 0}){this.priority=e,this.ordered=s,this.maxLatency=r,this.startGroup=i,this.endGroup=n}async#e(e,s){switch(s){case v.DRAFT_03:await e.u8(this.priority),await e.bool(this.ordered),await e.u53(this.maxLatency),await e.u53(this.startGroup!==void 0?this.startGroup+1:0),await e.u53(this.endGroup!==void 0?this.endGroup+1:0);break;case v.DRAFT_02:break;case v.DRAFT_01:await e.u8(this.priority??0);break;default:S(s)}}static async#t(e,s){let r,i,n,a,o;switch(e){case v.DRAFT_03:r=await s.u8(),i=await s.bool(),n=await s.u53(),a=await s.u53(),o=await s.u53();break;case v.DRAFT_02:break;case v.DRAFT_01:r=await s.u8();break;default:S(e)}return new Ve({priority:r,ordered:i,maxLatency:n,startGroup:a!==void 0&&a>0?a-1:void 0,endGroup:o!==void 0&&o>0?o-1:void 0})}async encode(e,s){return be(e,r=>this.#e(r,s))}static async decode(e,s){return ce(e,Ve.#t.bind(Ve,s))}}class jt{start;end;error;constructor(e){this.start=e.start,this.end=e.end,this.error=e.error}async#e(e){await e.u53(this.start),await e.u53(this.end),await e.u53(this.error)}static async#t(e){return new jt({start:await e.u53(),end:await e.u53(),error:await e.u53()})}async encode(e){return be(e,this.#e.bind(this))}static async decode(e){return ce(e,jt.#t)}}async function Zn(t,e,s){switch(s){case v.DRAFT_03:"ok"in e?(await t.u53(0),await e.ok.encode(t,s)):(await t.u53(1),await e.drop.encode(t));break;case v.DRAFT_01:case v.DRAFT_02:if("ok"in e)await e.ok.encode(t,s);else throw new Error("subscribe drop not supported for this version");break;default:S(s)}}async function Wn(t,e){switch(e){case v.DRAFT_03:{const s=await t.u53();switch(s){case 0:return{ok:await Ve.decode(t,e)};case 1:return{drop:await jt.decode(t)};default:throw new Error(`unknown subscribe response type: ${s}`)}}case v.DRAFT_01:case v.DRAFT_02:return{ok:await Ve.decode(t,e)};default:S(e)}}const ns=100,as=1e4,Hn=.25;class Jn{version;#e;#t=new f(new Map);constructor(e,s){this.#e=e,this.version=s}publish(e,s){this.#t.mutate(r=>{if(!r)throw new Error("closed");r.set(e,s)}),s.closed.finally(()=>{this.#t.mutate(r=>{r?.delete(e)})})}async runAnnounce(e,s){console.debug(`announce: prefix=${e.prefix}`);let r=new Set;const i=this.#t.peek();if(i){for(const n of i.keys()){const a=Lt(e.prefix,n);a!==null&&(console.debug(`announce: broadcast=${n} active=true`),r.add(a))}switch(this.version){case v.DRAFT_03:for(const n of r)await new Te({suffix:n,active:!0}).encode(s.writer,this.version);break;case v.DRAFT_01:case v.DRAFT_02:{await new Me([...r]).encode(s.writer,this.version);break}}for(;;){let n;const a=new Promise(u=>{n=this.#t.changed(u)}),o=await Promise.race([a,s.reader.closed]);if(n(),!o)break;const c=new Set;for(const u of o.keys()){const d=Lt(e.prefix,u);d!==null&&c.add(d)}for(const u of c.difference(r))console.debug(`announce: broadcast=${u} active=true`),await new Te({suffix:u,active:!0}).encode(s.writer,this.version);for(const u of r.difference(c))console.debug(`announce: broadcast=${u} active=false`),await new Te({suffix:u,active:!1}).encode(s.writer,this.version);r=c}}}async runSubscribe(e,s){const r=this.#t.peek()?.get(e.broadcast);if(!r){console.debug(`publish unknown: broadcast=${e.broadcast}`),s.writer.reset(new Error("not found"));return}const i=r.subscribe(e.track,e.priority);try{const n=new Ve({priority:e.priority});await Zn(s.writer,{ok:n},this.version),console.debug(`publish ok: broadcast=${e.broadcast} track=${i.name}`);const a=this.#s(e.id,e.broadcast,i,s.writer);for(;;){const o=Le.decodeMaybe(s.reader,this.version),c=await Promise.any([a,o]);if(!c)break;c instanceof Le&&console.warn("subscribe update not supported",c)}console.debug(`publish done: broadcast=${e.broadcast} track=${i.name}`),s.close(),i.close()}catch(n){const a=B(n);console.warn(`publish error: broadcast=${e.broadcast} track=${i.name} error=${a.message}`),i.close(a),s.abort(a)}}async#s(e,s,r,i){try{for(;;){const n=r.nextGroup(),a=await Promise.race([n,i.closed]);if(!a){n.then(o=>o?.close()).catch(()=>{});break}this.#r(e,a)}console.debug(`publish close: broadcast=${s} track=${r.name}`),r.close(),i.close()}catch(n){const a=B(n);console.warn(`publish error: broadcast=${s} track=${r.name} error=${a.message}`),r.close(a),i.reset(a)}}async#r(e,s){const r=new rt(e,s.sequence);try{const i=await Ke.open(this.#e);await i.u8(0),await r.encode(i);try{for(;;){const n=await Promise.race([s.readFrame(),i.closed]);if(!n)break;await i.u53(n.byteLength),await i.write(n)}i.close(),s.close()}catch(n){const a=B(n);i.reset(a),s.close(a)}}catch(i){const n=B(i);s.close(n)}}async runProbe(e){const s=this.#e;if(!s.getStats){e.abort(new Error("stats not supported"));return}let r,i;try{for(;;){const n=new Promise(d=>setTimeout(()=>d("timeout"),ns));if(await Promise.race([n,e.reader.closed])!=="timeout")break;const c=(await s.getStats()).estimatedSendRate;if(c==null)continue;let u;if(r===void 0||i===void 0)u=!0;else if(r===0)u=c>0;else{const d=performance.now()-i,h=Math.max(ns,Math.min(as,d)),p=as-ns,w=Hn*(as-h)/p;u=Math.abs(c-r)/r>=w}u&&(await new kt(c).encode(e.writer,this.version),r=c,i=performance.now())}}catch(n){const a=B(n);console.warn(`probe error: ${a.message}`),e.abort(a)}}close(){this.#t.update(e=>{for(const s of e?.values()??[])s.close()})}}class Ie{bitrate;constructor(e){this.bitrate=e}static#e(e){switch(e){case v.DRAFT_01:case v.DRAFT_02:break;case v.DRAFT_03:throw new Error("session info not supported for Draft03");default:S(e)}}async#t(e){await e.u53(this.bitrate)}static async#s(e){const s=await e.u53();return new Ie(s)}async encode(e,s){return Ie.#e(s),be(e,this.#t.bind(this))}static async decode(e,s){return Ie.#e(s),ce(e,Ie.#s)}static async decodeMaybe(e,s){return Ie.#e(s),zt(e,Ie.#s)}}const Ae={Session:0,Announce:1,Subscribe:2,Fetch:3,Probe:4,ClientCompat:32,ServerCompat:33};class Xn{#e;version;#t=new Map;#s=0n;constructor(e,s){this.#e=e,this.version=s}announced(e=wt()){const s=new xs;return this.#r(s,e),s}async#r(e,s){console.debug(`announced: prefix=${s}`);const r=new Rt(s);try{const i=await ot.open(this.#e);switch(await i.writer.u53(Ae.Announce),await r.encode(i.writer),this.version){case v.DRAFT_01:case v.DRAFT_02:{const n=await Me.decode(i.reader,this.version);for(const a of n.suffixes){const o=Bt(s,a);console.debug(`announced: broadcast=${o} active=true`),e.append({path:o,active:!0})}break}case v.DRAFT_03:break}for(;;){const n=await Promise.race([Te.decodeMaybe(i.reader,this.version),e.closed]);if(!n)break;if(n instanceof Error)throw n;const a=Bt(s,n.suffix);console.debug(`announced: broadcast=${a} active=${n.active}`),e.append({path:a,active:n.active})}e.close()}catch(i){e.close(B(i))}}consume(e){const s=new Qr;return(async()=>{for(;;){const r=await s.requested();if(!r)break;this.#i(e,r)}})(),s}async#i(e,s){const r=this.#s++;this.#t.set(r,s.track),console.debug(`subscribe start: id=${r} broadcast=${e} track=${s.track.name}`);const i=new it({id:r,broadcast:e,track:s.track.name,priority:s.priority}),n=await ot.open(this.#e);await n.writer.u53(Ae.Subscribe),await i.encode(n.writer,this.version);try{if(!("ok"in await Wn(n.reader,this.version)))throw new Error("first subscribe response must be SUBSCRIBE_OK");console.debug(`subscribe ok: id=${r} broadcast=${e} track=${s.track.name}`),await Promise.race([n.reader.closed,s.track.closed]),s.track.close(),n.close(),console.debug(`subscribe close: id=${r} broadcast=${e} track=${s.track.name}`)}catch(a){const o=B(a);s.track.close(o),console.warn(`subscribe error: id=${r} broadcast=${e} track=${s.track.name} error=${o.message}`),n.abort(o)}finally{this.#t.delete(r)}}async runGroup(e,s){const r=this.#t.get(e.subscribe);if(!r){if(e.subscribe>=this.#s)throw new Error(`unknown subscription: id=${e.subscribe}`);return}const i=new Ts(e.sequence);r.writeGroup(i);try{for(;await Promise.race([s.done(),r.closed,i.closed])===!1;){const a=await s.u53(),o=await s.read(a);if(!o)break;i.writeFrame(o)}i.close(),s.stop(new Error("cancel"))}catch(n){const a=B(n);i.close(a),s.stop(a)}}close(){for(const e of this.#t.values())e.close();this.#t.clear()}}class rr{url;version;#e;#t;#s;#r;#i=!1;constructor(e,s,r,i){this.url=e,this.#e=s,this.#t=i,this.version=r,this.#s=new Jn(this.#e,this.version),this.#r=new Xn(this.#e,this.version),this.#n()}close(){if(!this.#i){this.#i=!0,this.#s.close(),this.#r.close();try{this.#e.close()}catch{}}}async#n(){const e=this.#a(),s=this.#o(),r=this.#d();try{await Promise.all([e,s,r])}catch(i){this.#i||console.error("fatal error running connection",i)}finally{this.close()}}publish(e,s){this.#s.publish(e,s)}announced(e=wt()){return this.#r.announced(e)}consume(e){return this.#r.consume(e)}async#a(){if(this.#t)try{for(;await Ie.decodeMaybe(this.#t.reader,this.version););}finally{console.debug("session stream closed")}}async#o(){for(;;){const e=await ot.accept(this.#e);if(!e)break;this.#c(e).catch(s=>{e.writer.reset(s)}).finally(()=>{e.writer.close()})}}async#c(e){const s=await e.reader.u53();if(s===Ae.Session)throw new Error("duplicate session stream");if(s===Ae.Announce){const r=await Rt.decode(e.reader);await this.#s.runAnnounce(r,e);return}else if(s===Ae.Subscribe){const r=await it.decode(e.reader,this.version);await this.#s.runSubscribe(r,e);return}else if(s===Ae.Probe){await this.#s.runProbe(e);return}else throw new Error(`unknown stream type: ${s.toString()}`)}async#d(){const e=new ni(this.#e);for(;;){const s=await e.next();if(!s)break;this.#l(s).then(()=>{s.stop(new Error("cancel"))}).catch(r=>{s.stop(r)})}}async#l(e){const s=await e.u8();if(s===0){const r=await rt.decode(e);await this.#r.runGroup(r,e)}else throw new Error(`unknown stream type: ${s.toString()}`)}get closed(){return this.#e.closed.then(()=>{})}}function Kn(t){if(t=t.startsWith("0x")?t.slice(2):t,t.length%2)throw new Error("invalid hex string length");const e=t.match(/.{2}/g);if(!e)throw new Error("invalid hex string format");return new Uint8Array(e.map(s=>parseInt(s,16)))}const ir=new Set;async function Yn(t,e){let s;const r=new Promise(D=>{s=D}),i=globalThis.WebTransport?Qn(t,r,e?.webtransport):void 0,n=!i||ir.has(t.toString())?0:e?.websocket?.delay??200,a=e?.websocket?.enabled!==!1?ea(e?.websocket?.url??t,n,r):void 0;if(!a&&!i)throw new Error("no transport available; WebTransport not supported and WebSocket is disabled");const o=await Promise.any(i?a?[a,i]:[i]:[a]);if(s&&s(),!o)throw new Error("no transport available");o instanceof Kt&&(console.warn(t.toString(),"using WebSocket fallback; the user experience may be degraded"),ir.add(t.toString()));const c=typeof WebTransport<"u"&&o instanceof WebTransport?o.protocol:void 0;console.debug(t.toString(),"negotiated ALPN:",c??"(none)");let u;if(c===Vt.DRAFT_16)u=m.DRAFT_16;else if(c===Vt.DRAFT_15)u=m.DRAFT_15;else{if(c===di)return console.debug(t.toString(),"moq-lite draft-03 session established"),new rr(t,o,v.DRAFT_03,void 0);if(c===ui||c===""||c===void 0)u=m.DRAFT_14;else throw new Error(`unsupported WebTransport protocol: ${c}`)}const d=await ot.open(o);await d.writer.u53(Ae.ClientCompat);const h=new TextEncoder,p=new j;p.setVarint(rs.MaxRequestId,42069n),p.setBytes(rs.Implementation,h.encode("moq-lite-js"));const w=new Y({versions:u===m.DRAFT_16?[m.DRAFT_16]:u===m.DRAFT_15?[m.DRAFT_15]:[v.DRAFT_02,v.DRAFT_01,m.DRAFT_14],parameters:p});console.debug(t.toString(),"sending client setup",w),await w.encode(d.writer,u);const y=await d.reader.u53();if(y!==Ae.ServerCompat)throw new Error(`unsupported server message type: ${y.toString()}`);const E=await Q.decode(d.reader,u);if(console.debug(t.toString(),"received server setup",E),Object.values(v).includes(E.version))return console.debug(t.toString(),"moq-lite session established"),new rr(t,o,E.version,d);if(Object.values(m).includes(E.version)){const D=E.parameters.getVarint(rs.MaxRequestId)??0n;return console.debug(t.toString(),"moq-ietf session established, version:",E.version.toString(16)),new Gn({url:t,quic:o,control:d,maxRequestId:D,version:E.version})}else throw new Error(`unsupported server version: ${E.version.toString()}`)}async function Qn(t,e,s){let r=t;const i={allowPooling:!1,congestionControl:"low-latency",protocols:[di,ui,Vt.DRAFT_16,Vt.DRAFT_15],...s};if(t.protocol==="http:"){const o=new URL(t);o.pathname="/certificate.sha256",o.search="",console.warn(o.toString(),"performing an insecure fingerprint fetch; use https:// in production");const c=await Promise.race([fetch(o),e]);if(!c)return;const u=await Promise.race([c.text(),e]);if(u===void 0)return;i.serverCertificateHashes=(i.serverCertificateHashes||[]).concat([{algorithm:"sha-256",value:Kn(u)}]),r=new URL(t),r.protocol="https:"}const n=new WebTransport(r,i);if(!await Promise.race([n.ready.then(()=>!0),e])){n.close();return}return n}async function ea(t,e,s){const r=new Promise(o=>setTimeout(o,e));if(!await Promise.race([s,r.then(()=>!0)]))return;e&&console.debug(t.toString(),`no WebTransport after ${e}ms, attempting WebSocket fallback`);const n=new Kt(t);if(!await Promise.race([n.ready.then(()=>!0),s])){n.close();return}return n}class ta{url;enabled;status=new f("disconnected");established=new f(void 0);webtransport;websocket;delay;signals=new C;#e;#t=new f(0);constructor(e){this.url=f.from(e?.url),this.enabled=f.from(e?.enabled??!1),this.delay=e?.delay??{initial:1e3,multiplier:2,max:3e4},this.webtransport=e?.webtransport,this.websocket=e?.websocket,this.#e=this.delay.initial,this.signals.run(this.#s.bind(this))}#s(e){if(e.get(this.#t),!e.get(this.enabled))return;const r=e.get(this.url);r&&(e.set(this.status,"connecting","disconnected"),e.spawn(async()=>{try{const i=Yn(r,{websocket:this.websocket,webtransport:this.webtransport}),n=await Promise.race([e.cancel,i]);if(!n){i.then(a=>a.close()).catch(()=>{});return}e.set(this.established,n),e.cleanup(()=>n.close()),e.set(this.status,"connected","disconnected"),this.#e=this.delay.initial,await Promise.race([e.cancel,n.closed])}catch(i){console.warn("connection error:",i);const n=this.#t.peek()+1;e.timer(()=>this.#t.update(a=>Math.max(a,n)),this.#e),this.#e=Math.min(this.#e*this.delay.multiplier,this.delay.max)}}))}close(){this.signals.close()}}const Rs={zero:0,fromNano:t=>t/1e3,fromMilli:t=>t*1e3,fromSecond:t=>t*1e6,toNano:t=>t*1e3,toMilli:t=>t/1e3,toSecond:t=>t/1e6,now:()=>performance.now()*1e3,add:(t,e)=>t+e,sub:(t,e)=>t-e,mul:(t,e)=>t*e,div:(t,e)=>t/e,max:(t,e)=>Math.max(t,e),min:(t,e)=>Math.min(t,e)},g={zero:0,fromNano:t=>t/1e6,fromMicro:t=>t/1e3,fromSecond:t=>t*1e3,toNano:t=>t*1e6,toMicro:t=>t*1e3,toSecond:t=>t/1e3,now:()=>performance.now(),add:(t,e)=>t+e,sub:(t,e)=>t-e,mul:(t,e)=>t*e,div:(t,e)=>t/e,max:(t,e)=>Math.max(t,e),min:(t,e)=>Math.min(t,e)},sa="modulepreload",ra=function(t,e){return new URL(t,e).href},nr={},ar=function(e,s,r){let i=Promise.resolve();if(s&&s.length>0){let u=function(d){return Promise.all(d.map(h=>Promise.resolve(h).then(p=>({status:"fulfilled",value:p}),p=>({status:"rejected",reason:p}))))};const a=document.getElementsByTagName("link"),o=document.querySelector("meta[property=csp-nonce]"),c=o?.nonce||o?.getAttribute("nonce");i=u(s.map(d=>{if(d=ra(d,r),d in nr)return;nr[d]=!0;const h=d.endsWith(".css"),p=h?'[rel="stylesheet"]':"";if(r)for(let y=a.length-1;y>=0;y--){const E=a[y];if(E.href===d&&(!h||E.rel==="stylesheet"))return}else if(document.querySelector(`link[href="${d}"]${p}`))return;const w=document.createElement("link");if(w.rel=h?"stylesheet":sa,h||(w.as="script"),w.crossOrigin="",w.href=d,c&&w.setAttribute("nonce",c),document.head.appendChild(w),h)return new Promise((y,E)=>{w.addEventListener("load",y),w.addEventListener("error",()=>E(new Error(`Unable to preload CSS for ${d}`)))})}))}function n(a){const o=new Event("vite:preloadError",{cancelable:!0});if(o.payload=a,window.dispatchEvent(o),!o.defaultPrevented)throw a}return i.then(a=>{for(const o of a||[])o.status==="rejected"&&n(o.reason);return e().catch(n)})};function l(t,e,s){function r(o,c){if(o._zod||Object.defineProperty(o,"_zod",{value:{def:c,constr:a,traits:new Set},enumerable:!1}),o._zod.traits.has(t))return;o._zod.traits.add(t),e(o,c);const u=a.prototype,d=Object.keys(u);for(let h=0;h<d.length;h++){const p=d[h];p in o||(o[p]=u[p].bind(o))}}const i=s?.Parent??Object;class n extends i{}Object.defineProperty(n,"name",{value:t});function a(o){var c;const u=s?.Parent?new n:this;r(u,o),(c=u._zod).deferred??(c.deferred=[]);for(const d of u._zod.deferred)d();return u}return Object.defineProperty(a,"init",{value:r}),Object.defineProperty(a,Symbol.hasInstance,{value:o=>s?.Parent&&o instanceof s.Parent?!0:o?._zod?.traits?.has(t)}),Object.defineProperty(a,"name",{value:t}),a}class nt extends Error{constructor(){super("Encountered Promise during synchronous parse. Use .parseAsync() instead.")}}class li extends Error{constructor(e){super(`Encountered unidirectional transform during encode: ${e}`),this.name="ZodEncodeError"}}const hi={};function Ue(t){return hi}function fi(t){const e=Object.values(t).filter(s=>typeof s=="number");return Object.entries(t).filter(([s,r])=>e.indexOf(+s)===-1).map(([s,r])=>r)}function ys(t,e){return typeof e=="bigint"?e.toString():e}function Qt(t){return{get value(){{const e=t();return Object.defineProperty(this,"value",{value:e}),e}}}}function Ps(t){return t==null}function Os(t){const e=t.startsWith("^")?1:0,s=t.endsWith("$")?t.length-1:t.length;return t.slice(e,s)}function ia(t,e){const s=(t.toString().split(".")[1]||"").length,r=e.toString();let i=(r.split(".")[1]||"").length;if(i===0&&/\d?e-\d?/.test(r)){const c=r.match(/\d?e-(\d?)/);c?.[1]&&(i=Number.parseInt(c[1]))}const n=s>i?s:i,a=Number.parseInt(t.toFixed(n).replace(".","")),o=Number.parseInt(e.toFixed(n).replace(".",""));return a%o/10**n}const or=Symbol("evaluating");function I(t,e,s){let r;Object.defineProperty(t,e,{get(){if(r!==or)return r===void 0&&(r=or,r=s()),r},set(i){Object.defineProperty(t,e,{value:i})},configurable:!0})}function Ye(t,e,s){Object.defineProperty(t,e,{value:s,writable:!0,enumerable:!0,configurable:!0})}function $e(...t){const e={};for(const s of t){const r=Object.getOwnPropertyDescriptors(s);Object.assign(e,r)}return Object.defineProperties({},e)}function cr(t){return JSON.stringify(t)}function na(t){return t.toLowerCase().trim().replace(/[^\w\s-]/g,"").replace(/[\s_-]+/g,"-").replace(/^-+|-+$/g,"")}const pi="captureStackTrace"in Error?Error.captureStackTrace:(...t)=>{};function Pt(t){return typeof t=="object"&&t!==null&&!Array.isArray(t)}const aa=Qt(()=>{if(typeof navigator<"u"&&navigator?.userAgent?.includes("Cloudflare"))return!1;try{const t=Function;return new t(""),!0}catch{return!1}});function ht(t){if(Pt(t)===!1)return!1;const e=t.constructor;if(e===void 0||typeof e!="function")return!0;const s=e.prototype;return!(Pt(s)===!1||Object.prototype.hasOwnProperty.call(s,"isPrototypeOf")===!1)}function wi(t){return ht(t)?{...t}:Array.isArray(t)?[...t]:t}const oa=new Set(["string","number","symbol"]);function ft(t){return t.replace(/[.*+?^${}()|[\]\\]/g,"\\$&")}function Ce(t,e,s){const r=new t._zod.constr(e??t._zod.def);return(!e||s?.parent)&&(r._zod.parent=t),r}function b(t){const e=t;if(!e)return{};if(typeof e=="string")return{error:()=>e};if(e?.message!==void 0){if(e?.error!==void 0)throw new Error("Cannot specify both `message` and `error` params");e.error=e.message}return delete e.message,typeof e.error=="string"?{...e,error:()=>e.error}:e}function ca(t){return Object.keys(t).filter(e=>t[e]._zod.optin==="optional"&&t[e]._zod.optout==="optional")}const ua={safeint:[Number.MIN_SAFE_INTEGER,Number.MAX_SAFE_INTEGER],int32:[-2147483648,2147483647],uint32:[0,4294967295],float32:[-34028234663852886e22,34028234663852886e22],float64:[-Number.MAX_VALUE,Number.MAX_VALUE]};function da(t,e){const s=t._zod.def,r=s.checks;if(r&&r.length>0)throw new Error(".pick() cannot be used on object schemas containing refinements");const i=$e(t._zod.def,{get shape(){const n={};for(const a in e){if(!(a in s.shape))throw new Error(`Unrecognized key: "${a}"`);e[a]&&(n[a]=s.shape[a])}return Ye(this,"shape",n),n},checks:[]});return Ce(t,i)}function la(t,e){const s=t._zod.def,r=s.checks;if(r&&r.length>0)throw new Error(".omit() cannot be used on object schemas containing refinements");const i=$e(t._zod.def,{get shape(){const n={...t._zod.def.shape};for(const a in e){if(!(a in s.shape))throw new Error(`Unrecognized key: "${a}"`);e[a]&&delete n[a]}return Ye(this,"shape",n),n},checks:[]});return Ce(t,i)}function ha(t,e){if(!ht(e))throw new Error("Invalid input to extend: expected a plain object");const s=t._zod.def.checks;if(s&&s.length>0){const i=t._zod.def.shape;for(const n in e)if(Object.getOwnPropertyDescriptor(i,n)!==void 0)throw new Error("Cannot overwrite keys on object schemas containing refinements. Use `.safeExtend()` instead.")}const r=$e(t._zod.def,{get shape(){const i={...t._zod.def.shape,...e};return Ye(this,"shape",i),i}});return Ce(t,r)}function fa(t,e){if(!ht(e))throw new Error("Invalid input to safeExtend: expected a plain object");const s=$e(t._zod.def,{get shape(){const r={...t._zod.def.shape,...e};return Ye(this,"shape",r),r}});return Ce(t,s)}function pa(t,e){const s=$e(t._zod.def,{get shape(){const r={...t._zod.def.shape,...e._zod.def.shape};return Ye(this,"shape",r),r},get catchall(){return e._zod.def.catchall},checks:[]});return Ce(t,s)}function wa(t,e,s){const r=e._zod.def.checks;if(r&&r.length>0)throw new Error(".partial() cannot be used on object schemas containing refinements");const i=$e(e._zod.def,{get shape(){const n=e._zod.def.shape,a={...n};if(s)for(const o in s){if(!(o in n))throw new Error(`Unrecognized key: "${o}"`);s[o]&&(a[o]=t?new t({type:"optional",innerType:n[o]}):n[o])}else for(const o in n)a[o]=t?new t({type:"optional",innerType:n[o]}):n[o];return Ye(this,"shape",a),a},checks:[]});return Ce(e,i)}function ma(t,e,s){const r=$e(e._zod.def,{get shape(){const i=e._zod.def.shape,n={...i};if(s)for(const a in s){if(!(a in n))throw new Error(`Unrecognized key: "${a}"`);s[a]&&(n[a]=new t({type:"nonoptional",innerType:i[a]}))}else for(const a in i)n[a]=new t({type:"nonoptional",innerType:i[a]});return Ye(this,"shape",n),n}});return Ce(e,r)}function Qe(t,e=0){if(t.aborted===!0)return!0;for(let s=e;s<t.issues.length;s++)if(t.issues[s]?.continue!==!0)return!0;return!1}function et(t,e){return e.map(s=>{var r;return(r=s).path??(r.path=[]),s.path.unshift(t),s})}function qt(t){return typeof t=="string"?t:t?.message}function ze(t,e,s){const r={...t,path:t.path??[]};if(!t.message){const i=qt(t.inst?._zod.def?.error?.(t))??qt(e?.error?.(t))??qt(s.customError?.(t))??qt(s.localeError?.(t))??"Invalid input";r.message=i}return delete r.inst,delete r.continue,e?.reportInput||delete r.input,r}function Ns(t){return Array.isArray(t)?"array":typeof t=="string"?"string":"unknown"}function Ot(...t){const[e,s,r]=t;return typeof e=="string"?{message:e,code:"custom",input:s,inst:r}:{...e}}const mi=(t,e)=>{t.name="$ZodError",Object.defineProperty(t,"_zod",{value:t._zod,enumerable:!1}),Object.defineProperty(t,"issues",{value:e,enumerable:!1}),t.message=JSON.stringify(e,ys,2),Object.defineProperty(t,"toString",{value:()=>t.message,enumerable:!1})},bi=l("$ZodError",mi),gi=l("$ZodError",mi,{Parent:Error});function ba(t,e=s=>s.message){const s={},r=[];for(const i of t.issues)i.path.length>0?(s[i.path[0]]=s[i.path[0]]||[],s[i.path[0]].push(e(i))):r.push(e(i));return{formErrors:r,fieldErrors:s}}function ga(t,e=s=>s.message){const s={_errors:[]},r=i=>{for(const n of i.issues)if(n.code==="invalid_union"&&n.errors.length)n.errors.map(a=>r({issues:a}));else if(n.code==="invalid_key")r({issues:n.issues});else if(n.code==="invalid_element")r({issues:n.issues});else if(n.path.length===0)s._errors.push(e(n));else{let a=s,o=0;for(;o<n.path.length;){const c=n.path[o];o===n.path.length-1?(a[c]=a[c]||{_errors:[]},a[c]._errors.push(e(n))):a[c]=a[c]||{_errors:[]},a=a[c],o++}}};return r(t),s}const Us=t=>(e,s,r,i)=>{const n=r?Object.assign(r,{async:!1}):{async:!1},a=e._zod.run({value:s,issues:[]},n);if(a instanceof Promise)throw new nt;if(a.issues.length){const o=new(i?.Err??t)(a.issues.map(c=>ze(c,n,Ue())));throw pi(o,i?.callee),o}return a.value},zs=t=>async(e,s,r,i)=>{const n=r?Object.assign(r,{async:!0}):{async:!0};let a=e._zod.run({value:s,issues:[]},n);if(a instanceof Promise&&(a=await a),a.issues.length){const o=new(i?.Err??t)(a.issues.map(c=>ze(c,n,Ue())));throw pi(o,i?.callee),o}return a.value},es=t=>(e,s,r)=>{const i=r?{...r,async:!1}:{async:!1},n=e._zod.run({value:s,issues:[]},i);if(n instanceof Promise)throw new nt;return n.issues.length?{success:!1,error:new(t??bi)(n.issues.map(a=>ze(a,i,Ue())))}:{success:!0,data:n.value}},ya=es(gi),ts=t=>async(e,s,r)=>{const i=r?Object.assign(r,{async:!0}):{async:!0};let n=e._zod.run({value:s,issues:[]},i);return n instanceof Promise&&(n=await n),n.issues.length?{success:!1,error:new t(n.issues.map(a=>ze(a,i,Ue())))}:{success:!0,data:n.value}},va=ts(gi),_a=t=>(e,s,r)=>{const i=r?Object.assign(r,{direction:"backward"}):{direction:"backward"};return Us(t)(e,s,i)},ka=t=>(e,s,r)=>Us(t)(e,s,r),Ea=t=>async(e,s,r)=>{const i=r?Object.assign(r,{direction:"backward"}):{direction:"backward"};return zs(t)(e,s,i)},Ia=t=>async(e,s,r)=>zs(t)(e,s,r),Sa=t=>(e,s,r)=>{const i=r?Object.assign(r,{direction:"backward"}):{direction:"backward"};return es(t)(e,s,i)},Aa=t=>(e,s,r)=>es(t)(e,s,r),xa=t=>async(e,s,r)=>{const i=r?Object.assign(r,{direction:"backward"}):{direction:"backward"};return ts(t)(e,s,i)},Ta=t=>async(e,s,r)=>ts(t)(e,s,r),Ra=/^[cC][^\s-]{8,}$/,Pa=/^[0-9a-z]+$/,Oa=/^[0-9A-HJKMNP-TV-Za-hjkmnp-tv-z]{26}$/,Na=/^[0-9a-vA-V]{20}$/,Ua=/^[A-Za-z0-9]{27}$/,za=/^[a-zA-Z0-9_-]{21}$/,qa=/^P(?:(\d+W)|(?!.*W)(?=\d|T\d)(\d+Y)?(\d+M)?(\d+D)?(T(?=\d)(\d+H)?(\d+M)?(\d+([.,]\d+)?S)?)?)$/,$a=/^([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$/,ur=t=>t?new RegExp(`^([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-${t}[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12})$`):/^([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}|00000000-0000-0000-0000-000000000000|ffffffff-ffff-ffff-ffff-ffffffffffff)$/,Ca=/^(?!\.)(?!.*\.\.)([A-Za-z0-9_'+\-\.]*)[A-Za-z0-9_+-]@([A-Za-z0-9][A-Za-z0-9\-]*\.)+[A-Za-z]{2,}$/,Da="^(\\p{Extended_Pictographic}|\\p{Emoji_Component})+$";function Fa(){return new RegExp(Da,"u")}const Ma=/^(?:(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])\.){3}(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])$/,La=/^(([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:))$/,Ba=/^((25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])\.){3}(25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])\/([0-9]|[1-2][0-9]|3[0-2])$/,Va=/^(([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|::|([0-9a-fA-F]{1,4})?::([0-9a-fA-F]{1,4}:?){0,6})\/(12[0-8]|1[01][0-9]|[1-9]?[0-9])$/,ja=/^$|^(?:[0-9a-zA-Z+/]{4})*(?:(?:[0-9a-zA-Z+/]{2}==)|(?:[0-9a-zA-Z+/]{3}=))?$/,yi=/^[A-Za-z0-9_-]*$/,Ga=/^\+[1-9]\d{6,14}$/,vi="(?:(?:\\d\\d[2468][048]|\\d\\d[13579][26]|\\d\\d0[48]|[02468][048]00|[13579][26]00)-02-29|\\d{4}-(?:(?:0[13578]|1[02])-(?:0[1-9]|[12]\\d|3[01])|(?:0[469]|11)-(?:0[1-9]|[12]\\d|30)|(?:02)-(?:0[1-9]|1\\d|2[0-8])))",Za=new RegExp(`^${vi}$`);function _i(t){const e="(?:[01]\\d|2[0-3]):[0-5]\\d";return typeof t.precision=="number"?t.precision===-1?`${e}`:t.precision===0?`${e}:[0-5]\\d`:`${e}:[0-5]\\d\\.\\d{${t.precision}}`:`${e}(?::[0-5]\\d(?:\\.\\d+)?)?`}function Wa(t){return new RegExp(`^${_i(t)}$`)}function Ha(t){const e=_i({precision:t.precision}),s=["Z"];t.local&&s.push(""),t.offset&&s.push("([+-](?:[01]\\d|2[0-3]):[0-5]\\d)");const r=`${e}(?:${s.join("|")})`;return new RegExp(`^${vi}T(?:${r})$`)}const Ja=t=>{const e=t?`[\\s\\S]{${t?.minimum??0},${t?.maximum??""}}`:"[\\s\\S]*";return new RegExp(`^${e}$`)},Xa=/^-?\d+$/,ki=/^-?\d+(?:\.\d+)?$/,Ka=/^(?:true|false)$/i,Ya=/^[^A-Z]*$/,Qa=/^[^a-z]*$/,G=l("$ZodCheck",(t,e)=>{var s;t._zod??(t._zod={}),t._zod.def=e,(s=t._zod).onattach??(s.onattach=[])}),Ei={number:"number",bigint:"bigint",object:"date"},Ii=l("$ZodCheckLessThan",(t,e)=>{G.init(t,e);const s=Ei[typeof e.value];t._zod.onattach.push(r=>{const i=r._zod.bag,n=(e.inclusive?i.maximum:i.exclusiveMaximum)??Number.POSITIVE_INFINITY;e.value<n&&(e.inclusive?i.maximum=e.value:i.exclusiveMaximum=e.value)}),t._zod.check=r=>{(e.inclusive?r.value<=e.value:r.value<e.value)||r.issues.push({origin:s,code:"too_big",maximum:typeof e.value=="object"?e.value.getTime():e.value,input:r.value,inclusive:e.inclusive,inst:t,continue:!e.abort})}}),Si=l("$ZodCheckGreaterThan",(t,e)=>{G.init(t,e);const s=Ei[typeof e.value];t._zod.onattach.push(r=>{const i=r._zod.bag,n=(e.inclusive?i.minimum:i.exclusiveMinimum)??Number.NEGATIVE_INFINITY;e.value>n&&(e.inclusive?i.minimum=e.value:i.exclusiveMinimum=e.value)}),t._zod.check=r=>{(e.inclusive?r.value>=e.value:r.value>e.value)||r.issues.push({origin:s,code:"too_small",minimum:typeof e.value=="object"?e.value.getTime():e.value,input:r.value,inclusive:e.inclusive,inst:t,continue:!e.abort})}}),eo=l("$ZodCheckMultipleOf",(t,e)=>{G.init(t,e),t._zod.onattach.push(s=>{var r;(r=s._zod.bag).multipleOf??(r.multipleOf=e.value)}),t._zod.check=s=>{if(typeof s.value!=typeof e.value)throw new Error("Cannot mix number and bigint in multiple_of check.");(typeof s.value=="bigint"?s.value%e.value===BigInt(0):ia(s.value,e.value)===0)||s.issues.push({origin:typeof s.value,code:"not_multiple_of",divisor:e.value,input:s.value,inst:t,continue:!e.abort})}}),to=l("$ZodCheckNumberFormat",(t,e)=>{G.init(t,e),e.format=e.format||"float64";const s=e.format?.includes("int"),r=s?"int":"number",[i,n]=ua[e.format];t._zod.onattach.push(a=>{const o=a._zod.bag;o.format=e.format,o.minimum=i,o.maximum=n,s&&(o.pattern=Xa)}),t._zod.check=a=>{const o=a.value;if(s){if(!Number.isInteger(o)){a.issues.push({expected:r,format:e.format,code:"invalid_type",continue:!1,input:o,inst:t});return}if(!Number.isSafeInteger(o)){o>0?a.issues.push({input:o,code:"too_big",maximum:Number.MAX_SAFE_INTEGER,note:"Integers must be within the safe integer range.",inst:t,origin:r,inclusive:!0,continue:!e.abort}):a.issues.push({input:o,code:"too_small",minimum:Number.MIN_SAFE_INTEGER,note:"Integers must be within the safe integer range.",inst:t,origin:r,inclusive:!0,continue:!e.abort});return}}o<i&&a.issues.push({origin:"number",input:o,code:"too_small",minimum:i,inclusive:!0,inst:t,continue:!e.abort}),o>n&&a.issues.push({origin:"number",input:o,code:"too_big",maximum:n,inclusive:!0,inst:t,continue:!e.abort})}}),so=l("$ZodCheckMaxLength",(t,e)=>{var s;G.init(t,e),(s=t._zod.def).when??(s.when=r=>{const i=r.value;return!Ps(i)&&i.length!==void 0}),t._zod.onattach.push(r=>{const i=r._zod.bag.maximum??Number.POSITIVE_INFINITY;e.maximum<i&&(r._zod.bag.maximum=e.maximum)}),t._zod.check=r=>{const i=r.value;if(i.length<=e.maximum)return;const n=Ns(i);r.issues.push({origin:n,code:"too_big",maximum:e.maximum,inclusive:!0,input:i,inst:t,continue:!e.abort})}}),ro=l("$ZodCheckMinLength",(t,e)=>{var s;G.init(t,e),(s=t._zod.def).when??(s.when=r=>{const i=r.value;return!Ps(i)&&i.length!==void 0}),t._zod.onattach.push(r=>{const i=r._zod.bag.minimum??Number.NEGATIVE_INFINITY;e.minimum>i&&(r._zod.bag.minimum=e.minimum)}),t._zod.check=r=>{const i=r.value;if(i.length>=e.minimum)return;const n=Ns(i);r.issues.push({origin:n,code:"too_small",minimum:e.minimum,inclusive:!0,input:i,inst:t,continue:!e.abort})}}),io=l("$ZodCheckLengthEquals",(t,e)=>{var s;G.init(t,e),(s=t._zod.def).when??(s.when=r=>{const i=r.value;return!Ps(i)&&i.length!==void 0}),t._zod.onattach.push(r=>{const i=r._zod.bag;i.minimum=e.length,i.maximum=e.length,i.length=e.length}),t._zod.check=r=>{const i=r.value,n=i.length;if(n===e.length)return;const a=Ns(i),o=n>e.length;r.issues.push({origin:a,...o?{code:"too_big",maximum:e.length}:{code:"too_small",minimum:e.length},inclusive:!0,exact:!0,input:r.value,inst:t,continue:!e.abort})}}),ss=l("$ZodCheckStringFormat",(t,e)=>{var s,r;G.init(t,e),t._zod.onattach.push(i=>{const n=i._zod.bag;n.format=e.format,e.pattern&&(n.patterns??(n.patterns=new Set),n.patterns.add(e.pattern))}),e.pattern?(s=t._zod).check??(s.check=i=>{e.pattern.lastIndex=0,!e.pattern.test(i.value)&&i.issues.push({origin:"string",code:"invalid_format",format:e.format,input:i.value,...e.pattern?{pattern:e.pattern.toString()}:{},inst:t,continue:!e.abort})}):(r=t._zod).check??(r.check=()=>{})}),no=l("$ZodCheckRegex",(t,e)=>{ss.init(t,e),t._zod.check=s=>{e.pattern.lastIndex=0,!e.pattern.test(s.value)&&s.issues.push({origin:"string",code:"invalid_format",format:"regex",input:s.value,pattern:e.pattern.toString(),inst:t,continue:!e.abort})}}),ao=l("$ZodCheckLowerCase",(t,e)=>{e.pattern??(e.pattern=Ya),ss.init(t,e)}),oo=l("$ZodCheckUpperCase",(t,e)=>{e.pattern??(e.pattern=Qa),ss.init(t,e)}),co=l("$ZodCheckIncludes",(t,e)=>{G.init(t,e);const s=ft(e.includes),r=new RegExp(typeof e.position=="number"?`^.{${e.position}}${s}`:s);e.pattern=r,t._zod.onattach.push(i=>{const n=i._zod.bag;n.patterns??(n.patterns=new Set),n.patterns.add(r)}),t._zod.check=i=>{i.value.includes(e.includes,e.position)||i.issues.push({origin:"string",code:"invalid_format",format:"includes",includes:e.includes,input:i.value,inst:t,continue:!e.abort})}}),uo=l("$ZodCheckStartsWith",(t,e)=>{G.init(t,e);const s=new RegExp(`^${ft(e.prefix)}.*`);e.pattern??(e.pattern=s),t._zod.onattach.push(r=>{const i=r._zod.bag;i.patterns??(i.patterns=new Set),i.patterns.add(s)}),t._zod.check=r=>{r.value.startsWith(e.prefix)||r.issues.push({origin:"string",code:"invalid_format",format:"starts_with",prefix:e.prefix,input:r.value,inst:t,continue:!e.abort})}}),lo=l("$ZodCheckEndsWith",(t,e)=>{G.init(t,e);const s=new RegExp(`.*${ft(e.suffix)}$`);e.pattern??(e.pattern=s),t._zod.onattach.push(r=>{const i=r._zod.bag;i.patterns??(i.patterns=new Set),i.patterns.add(s)}),t._zod.check=r=>{r.value.endsWith(e.suffix)||r.issues.push({origin:"string",code:"invalid_format",format:"ends_with",suffix:e.suffix,input:r.value,inst:t,continue:!e.abort})}}),ho=l("$ZodCheckOverwrite",(t,e)=>{G.init(t,e),t._zod.check=s=>{s.value=e.tx(s.value)}});class fo{constructor(e=[]){this.content=[],this.indent=0,this&&(this.args=e)}indented(e){this.indent+=1,e(this),this.indent-=1}write(e){if(typeof e=="function"){e(this,{execution:"sync"}),e(this,{execution:"async"});return}const s=e.split(`
`).filter(n=>n),r=Math.min(...s.map(n=>n.length-n.trimStart().length)),i=s.map(n=>n.slice(r)).map(n=>" ".repeat(this.indent*2)+n);for(const n of i)this.content.push(n)}compile(){const e=Function,s=this?.args,r=[...(this?.content??[""]).map(i=>`  ${i}`)];return new e(...s,r.join(`
`))}}const po={major:4,minor:3,patch:6},P=l("$ZodType",(t,e)=>{var s;t??(t={}),t._zod.def=e,t._zod.bag=t._zod.bag||{},t._zod.version=po;const r=[...t._zod.def.checks??[]];t._zod.traits.has("$ZodCheck")&&r.unshift(t);for(const i of r)for(const n of i._zod.onattach)n(t);if(r.length===0)(s=t._zod).deferred??(s.deferred=[]),t._zod.deferred?.push(()=>{t._zod.run=t._zod.parse});else{const i=(a,o,c)=>{let u=Qe(a),d;for(const h of o){if(h._zod.def.when){if(!h._zod.def.when(a))continue}else if(u)continue;const p=a.issues.length,w=h._zod.check(a);if(w instanceof Promise&&c?.async===!1)throw new nt;if(d||w instanceof Promise)d=(d??Promise.resolve()).then(async()=>{await w,a.issues.length!==p&&(u||(u=Qe(a,p)))});else{if(a.issues.length===p)continue;u||(u=Qe(a,p))}}return d?d.then(()=>a):a},n=(a,o,c)=>{if(Qe(a))return a.aborted=!0,a;const u=i(o,r,c);if(u instanceof Promise){if(c.async===!1)throw new nt;return u.then(d=>t._zod.parse(d,c))}return t._zod.parse(u,c)};t._zod.run=(a,o)=>{if(o.skipChecks)return t._zod.parse(a,o);if(o.direction==="backward"){const u=t._zod.parse({value:a.value,issues:[]},{...o,skipChecks:!0});return u instanceof Promise?u.then(d=>n(d,a,o)):n(u,a,o)}const c=t._zod.parse(a,o);if(c instanceof Promise){if(o.async===!1)throw new nt;return c.then(u=>i(u,r,o))}return i(c,r,o)}}I(t,"~standard",()=>({validate:i=>{try{const n=ya(t,i);return n.success?{value:n.data}:{issues:n.error?.issues}}catch{return va(t,i).then(n=>n.success?{value:n.data}:{issues:n.error?.issues})}},vendor:"zod",version:1}))}),qs=l("$ZodString",(t,e)=>{P.init(t,e),t._zod.pattern=[...t?._zod.bag?.patterns??[]].pop()??Ja(t._zod.bag),t._zod.parse=(s,r)=>{if(e.coerce)try{s.value=String(s.value)}catch{}return typeof s.value=="string"||s.issues.push({expected:"string",code:"invalid_type",input:s.value,inst:t}),s}}),x=l("$ZodStringFormat",(t,e)=>{ss.init(t,e),qs.init(t,e)}),wo=l("$ZodGUID",(t,e)=>{e.pattern??(e.pattern=$a),x.init(t,e)}),mo=l("$ZodUUID",(t,e)=>{if(e.version){const s={v1:1,v2:2,v3:3,v4:4,v5:5,v6:6,v7:7,v8:8}[e.version];if(s===void 0)throw new Error(`Invalid UUID version: "${e.version}"`);e.pattern??(e.pattern=ur(s))}else e.pattern??(e.pattern=ur());x.init(t,e)}),bo=l("$ZodEmail",(t,e)=>{e.pattern??(e.pattern=Ca),x.init(t,e)}),go=l("$ZodURL",(t,e)=>{x.init(t,e),t._zod.check=s=>{try{const r=s.value.trim(),i=new URL(r);e.hostname&&(e.hostname.lastIndex=0,e.hostname.test(i.hostname)||s.issues.push({code:"invalid_format",format:"url",note:"Invalid hostname",pattern:e.hostname.source,input:s.value,inst:t,continue:!e.abort})),e.protocol&&(e.protocol.lastIndex=0,e.protocol.test(i.protocol.endsWith(":")?i.protocol.slice(0,-1):i.protocol)||s.issues.push({code:"invalid_format",format:"url",note:"Invalid protocol",pattern:e.protocol.source,input:s.value,inst:t,continue:!e.abort})),e.normalize?s.value=i.href:s.value=r;return}catch{s.issues.push({code:"invalid_format",format:"url",input:s.value,inst:t,continue:!e.abort})}}}),yo=l("$ZodEmoji",(t,e)=>{e.pattern??(e.pattern=Fa()),x.init(t,e)}),vo=l("$ZodNanoID",(t,e)=>{e.pattern??(e.pattern=za),x.init(t,e)}),_o=l("$ZodCUID",(t,e)=>{e.pattern??(e.pattern=Ra),x.init(t,e)}),ko=l("$ZodCUID2",(t,e)=>{e.pattern??(e.pattern=Pa),x.init(t,e)}),Eo=l("$ZodULID",(t,e)=>{e.pattern??(e.pattern=Oa),x.init(t,e)}),Io=l("$ZodXID",(t,e)=>{e.pattern??(e.pattern=Na),x.init(t,e)}),So=l("$ZodKSUID",(t,e)=>{e.pattern??(e.pattern=Ua),x.init(t,e)}),Ao=l("$ZodISODateTime",(t,e)=>{e.pattern??(e.pattern=Ha(e)),x.init(t,e)}),xo=l("$ZodISODate",(t,e)=>{e.pattern??(e.pattern=Za),x.init(t,e)}),To=l("$ZodISOTime",(t,e)=>{e.pattern??(e.pattern=Wa(e)),x.init(t,e)}),Ro=l("$ZodISODuration",(t,e)=>{e.pattern??(e.pattern=qa),x.init(t,e)}),Po=l("$ZodIPv4",(t,e)=>{e.pattern??(e.pattern=Ma),x.init(t,e),t._zod.bag.format="ipv4"}),Oo=l("$ZodIPv6",(t,e)=>{e.pattern??(e.pattern=La),x.init(t,e),t._zod.bag.format="ipv6",t._zod.check=s=>{try{new URL(`http://[${s.value}]`)}catch{s.issues.push({code:"invalid_format",format:"ipv6",input:s.value,inst:t,continue:!e.abort})}}}),No=l("$ZodCIDRv4",(t,e)=>{e.pattern??(e.pattern=Ba),x.init(t,e)}),Uo=l("$ZodCIDRv6",(t,e)=>{e.pattern??(e.pattern=Va),x.init(t,e),t._zod.check=s=>{const r=s.value.split("/");try{if(r.length!==2)throw new Error;const[i,n]=r;if(!n)throw new Error;const a=Number(n);if(`${a}`!==n)throw new Error;if(a<0||a>128)throw new Error;new URL(`http://[${i}]`)}catch{s.issues.push({code:"invalid_format",format:"cidrv6",input:s.value,inst:t,continue:!e.abort})}}});function Ai(t){if(t==="")return!0;if(t.length%4!==0)return!1;try{return atob(t),!0}catch{return!1}}const zo=l("$ZodBase64",(t,e)=>{e.pattern??(e.pattern=ja),x.init(t,e),t._zod.bag.contentEncoding="base64",t._zod.check=s=>{Ai(s.value)||s.issues.push({code:"invalid_format",format:"base64",input:s.value,inst:t,continue:!e.abort})}});function qo(t){if(!yi.test(t))return!1;const e=t.replace(/[-_]/g,r=>r==="-"?"+":"/"),s=e.padEnd(Math.ceil(e.length/4)*4,"=");return Ai(s)}const $o=l("$ZodBase64URL",(t,e)=>{e.pattern??(e.pattern=yi),x.init(t,e),t._zod.bag.contentEncoding="base64url",t._zod.check=s=>{qo(s.value)||s.issues.push({code:"invalid_format",format:"base64url",input:s.value,inst:t,continue:!e.abort})}}),Co=l("$ZodE164",(t,e)=>{e.pattern??(e.pattern=Ga),x.init(t,e)});function Do(t,e=null){try{const s=t.split(".");if(s.length!==3)return!1;const[r]=s;if(!r)return!1;const i=JSON.parse(atob(r));return!("typ"in i&&i?.typ!=="JWT"||!i.alg||e&&(!("alg"in i)||i.alg!==e))}catch{return!1}}const Fo=l("$ZodJWT",(t,e)=>{x.init(t,e),t._zod.check=s=>{Do(s.value,e.alg)||s.issues.push({code:"invalid_format",format:"jwt",input:s.value,inst:t,continue:!e.abort})}}),xi=l("$ZodNumber",(t,e)=>{P.init(t,e),t._zod.pattern=t._zod.bag.pattern??ki,t._zod.parse=(s,r)=>{if(e.coerce)try{s.value=Number(s.value)}catch{}const i=s.value;if(typeof i=="number"&&!Number.isNaN(i)&&Number.isFinite(i))return s;const n=typeof i=="number"?Number.isNaN(i)?"NaN":Number.isFinite(i)?void 0:"Infinity":void 0;return s.issues.push({expected:"number",code:"invalid_type",input:i,inst:t,...n?{received:n}:{}}),s}}),Mo=l("$ZodNumberFormat",(t,e)=>{to.init(t,e),xi.init(t,e)}),Lo=l("$ZodBoolean",(t,e)=>{P.init(t,e),t._zod.pattern=Ka,t._zod.parse=(s,r)=>{if(e.coerce)try{s.value=!!s.value}catch{}const i=s.value;return typeof i=="boolean"||s.issues.push({expected:"boolean",code:"invalid_type",input:i,inst:t}),s}}),Bo=l("$ZodUnknown",(t,e)=>{P.init(t,e),t._zod.parse=s=>s}),Vo=l("$ZodNever",(t,e)=>{P.init(t,e),t._zod.parse=(s,r)=>(s.issues.push({expected:"never",code:"invalid_type",input:s.value,inst:t}),s)});function dr(t,e,s){t.issues.length&&e.issues.push(...et(s,t.issues)),e.value[s]=t.value}const jo=l("$ZodArray",(t,e)=>{P.init(t,e),t._zod.parse=(s,r)=>{const i=s.value;if(!Array.isArray(i))return s.issues.push({expected:"array",code:"invalid_type",input:i,inst:t}),s;s.value=Array(i.length);const n=[];for(let a=0;a<i.length;a++){const o=i[a],c=e.element._zod.run({value:o,issues:[]},r);c instanceof Promise?n.push(c.then(u=>dr(u,s,a))):dr(c,s,a)}return n.length?Promise.all(n).then(()=>s):s}});function Gt(t,e,s,r,i){if(t.issues.length){if(i&&!(s in r))return;e.issues.push(...et(s,t.issues))}t.value===void 0?s in r&&(e.value[s]=void 0):e.value[s]=t.value}function Ti(t){const e=Object.keys(t.shape);for(const r of e)if(!t.shape?.[r]?._zod?.traits?.has("$ZodType"))throw new Error(`Invalid element at key "${r}": expected a Zod schema`);const s=ca(t.shape);return{...t,keys:e,keySet:new Set(e),numKeys:e.length,optionalKeys:new Set(s)}}function Ri(t,e,s,r,i,n){const a=[],o=i.keySet,c=i.catchall._zod,u=c.def.type,d=c.optout==="optional";for(const h in e){if(o.has(h))continue;if(u==="never"){a.push(h);continue}const p=c.run({value:e[h],issues:[]},r);p instanceof Promise?t.push(p.then(w=>Gt(w,s,h,e,d))):Gt(p,s,h,e,d)}return a.length&&s.issues.push({code:"unrecognized_keys",keys:a,input:e,inst:n}),t.length?Promise.all(t).then(()=>s):s}const Go=l("$ZodObject",(t,e)=>{if(P.init(t,e),!Object.getOwnPropertyDescriptor(e,"shape")?.get){const a=e.shape;Object.defineProperty(e,"shape",{get:()=>{const o={...a};return Object.defineProperty(e,"shape",{value:o}),o}})}const s=Qt(()=>Ti(e));I(t._zod,"propValues",()=>{const a=e.shape,o={};for(const c in a){const u=a[c]._zod;if(u.values){o[c]??(o[c]=new Set);for(const d of u.values)o[c].add(d)}}return o});const r=Pt,i=e.catchall;let n;t._zod.parse=(a,o)=>{n??(n=s.value);const c=a.value;if(!r(c))return a.issues.push({expected:"object",code:"invalid_type",input:c,inst:t}),a;a.value={};const u=[],d=n.shape;for(const h of n.keys){const p=d[h],w=p._zod.optout==="optional",y=p._zod.run({value:c[h],issues:[]},o);y instanceof Promise?u.push(y.then(E=>Gt(E,a,h,c,w))):Gt(y,a,h,c,w)}return i?Ri(u,c,a,o,s.value,t):u.length?Promise.all(u).then(()=>a):a}}),Zo=l("$ZodObjectJIT",(t,e)=>{Go.init(t,e);const s=t._zod.parse,r=Qt(()=>Ti(e)),i=h=>{const p=new fo(["shape","payload","ctx"]),w=r.value,y=q=>{const A=cr(q);return`shape[${A}]._zod.run({ value: input[${A}], issues: [] }, ctx)`};p.write("const input = payload.value;");const E=Object.create(null);let D=0;for(const q of w.keys)E[q]=`key_${D++}`;p.write("const newResult = {};");for(const q of w.keys){const A=E[q],U=cr(q),Z=h[q]?._zod?.optout==="optional";p.write(`const ${A} = ${y(q)};`),Z?p.write(`
        if (${A}.issues.length) {
          if (${U} in input) {
            payload.issues = payload.issues.concat(${A}.issues.map(iss => ({
              ...iss,
              path: iss.path ? [${U}, ...iss.path] : [${U}]
            })));
          }
        }
        
        if (${A}.value === undefined) {
          if (${U} in input) {
            newResult[${U}] = undefined;
          }
        } else {
          newResult[${U}] = ${A}.value;
        }
        
      `):p.write(`
        if (${A}.issues.length) {
          payload.issues = payload.issues.concat(${A}.issues.map(iss => ({
            ...iss,
            path: iss.path ? [${U}, ...iss.path] : [${U}]
          })));
        }
        
        if (${A}.value === undefined) {
          if (${U} in input) {
            newResult[${U}] = undefined;
          }
        } else {
          newResult[${U}] = ${A}.value;
        }
        
      `)}p.write("payload.value = newResult;"),p.write("return payload;");const J=p.compile();return(q,A)=>J(h,q,A)};let n;const a=Pt,o=!hi.jitless,c=o&&aa.value,u=e.catchall;let d;t._zod.parse=(h,p)=>{d??(d=r.value);const w=h.value;return a(w)?o&&c&&p?.async===!1&&p.jitless!==!0?(n||(n=i(e.shape)),h=n(h,p),u?Ri([],w,h,p,d,t):h):s(h,p):(h.issues.push({expected:"object",code:"invalid_type",input:w,inst:t}),h)}});function lr(t,e,s,r){for(const n of t)if(n.issues.length===0)return e.value=n.value,e;const i=t.filter(n=>!Qe(n));return i.length===1?(e.value=i[0].value,i[0]):(e.issues.push({code:"invalid_union",input:e.value,inst:s,errors:t.map(n=>n.issues.map(a=>ze(a,r,Ue())))}),e)}const Pi=l("$ZodUnion",(t,e)=>{P.init(t,e),I(t._zod,"optin",()=>e.options.some(i=>i._zod.optin==="optional")?"optional":void 0),I(t._zod,"optout",()=>e.options.some(i=>i._zod.optout==="optional")?"optional":void 0),I(t._zod,"values",()=>{if(e.options.every(i=>i._zod.values))return new Set(e.options.flatMap(i=>Array.from(i._zod.values)))}),I(t._zod,"pattern",()=>{if(e.options.every(i=>i._zod.pattern)){const i=e.options.map(n=>n._zod.pattern);return new RegExp(`^(${i.map(n=>Os(n.source)).join("|")})$`)}});const s=e.options.length===1,r=e.options[0]._zod.run;t._zod.parse=(i,n)=>{if(s)return r(i,n);let a=!1;const o=[];for(const c of e.options){const u=c._zod.run({value:i.value,issues:[]},n);if(u instanceof Promise)o.push(u),a=!0;else{if(u.issues.length===0)return u;o.push(u)}}return a?Promise.all(o).then(c=>lr(c,i,t,n)):lr(o,i,t,n)}}),Wo=l("$ZodDiscriminatedUnion",(t,e)=>{e.inclusive=!1,Pi.init(t,e);const s=t._zod.parse;I(t._zod,"propValues",()=>{const i={};for(const n of e.options){const a=n._zod.propValues;if(!a||Object.keys(a).length===0)throw new Error(`Invalid discriminated union option at index "${e.options.indexOf(n)}"`);for(const[o,c]of Object.entries(a)){i[o]||(i[o]=new Set);for(const u of c)i[o].add(u)}}return i});const r=Qt(()=>{const i=e.options,n=new Map;for(const a of i){const o=a._zod.propValues?.[e.discriminator];if(!o||o.size===0)throw new Error(`Invalid discriminated union option at index "${e.options.indexOf(a)}"`);for(const c of o){if(n.has(c))throw new Error(`Duplicate discriminator value "${String(c)}"`);n.set(c,a)}}return n});t._zod.parse=(i,n)=>{const a=i.value;if(!Pt(a))return i.issues.push({code:"invalid_type",expected:"object",input:a,inst:t}),i;const o=r.value.get(a?.[e.discriminator]);return o?o._zod.run(i,n):e.unionFallback?s(i,n):(i.issues.push({code:"invalid_union",errors:[],note:"No matching discriminator",discriminator:e.discriminator,input:a,path:[e.discriminator],inst:t}),i)}}),Ho=l("$ZodIntersection",(t,e)=>{P.init(t,e),t._zod.parse=(s,r)=>{const i=s.value,n=e.left._zod.run({value:i,issues:[]},r),a=e.right._zod.run({value:i,issues:[]},r);return n instanceof Promise||a instanceof Promise?Promise.all([n,a]).then(([o,c])=>hr(s,o,c)):hr(s,n,a)}});function vs(t,e){if(t===e)return{valid:!0,data:t};if(t instanceof Date&&e instanceof Date&&+t==+e)return{valid:!0,data:t};if(ht(t)&&ht(e)){const s=Object.keys(e),r=Object.keys(t).filter(n=>s.indexOf(n)!==-1),i={...t,...e};for(const n of r){const a=vs(t[n],e[n]);if(!a.valid)return{valid:!1,mergeErrorPath:[n,...a.mergeErrorPath]};i[n]=a.data}return{valid:!0,data:i}}if(Array.isArray(t)&&Array.isArray(e)){if(t.length!==e.length)return{valid:!1,mergeErrorPath:[]};const s=[];for(let r=0;r<t.length;r++){const i=t[r],n=e[r],a=vs(i,n);if(!a.valid)return{valid:!1,mergeErrorPath:[r,...a.mergeErrorPath]};s.push(a.data)}return{valid:!0,data:s}}return{valid:!1,mergeErrorPath:[]}}function hr(t,e,s){const r=new Map;let i;for(const o of e.issues)if(o.code==="unrecognized_keys"){i??(i=o);for(const c of o.keys)r.has(c)||r.set(c,{}),r.get(c).l=!0}else t.issues.push(o);for(const o of s.issues)if(o.code==="unrecognized_keys")for(const c of o.keys)r.has(c)||r.set(c,{}),r.get(c).r=!0;else t.issues.push(o);const n=[...r].filter(([,o])=>o.l&&o.r).map(([o])=>o);if(n.length&&i&&t.issues.push({...i,keys:n}),Qe(t))return t;const a=vs(e.value,s.value);if(!a.valid)throw new Error(`Unmergable intersection. Error path: ${JSON.stringify(a.mergeErrorPath)}`);return t.value=a.data,t}const Jo=l("$ZodRecord",(t,e)=>{P.init(t,e),t._zod.parse=(s,r)=>{const i=s.value;if(!ht(i))return s.issues.push({expected:"record",code:"invalid_type",input:i,inst:t}),s;const n=[],a=e.keyType._zod.values;if(a){s.value={};const o=new Set;for(const u of a)if(typeof u=="string"||typeof u=="number"||typeof u=="symbol"){o.add(typeof u=="number"?u.toString():u);const d=e.valueType._zod.run({value:i[u],issues:[]},r);d instanceof Promise?n.push(d.then(h=>{h.issues.length&&s.issues.push(...et(u,h.issues)),s.value[u]=h.value})):(d.issues.length&&s.issues.push(...et(u,d.issues)),s.value[u]=d.value)}let c;for(const u in i)o.has(u)||(c=c??[],c.push(u));c&&c.length>0&&s.issues.push({code:"unrecognized_keys",input:i,inst:t,keys:c})}else{s.value={};for(const o of Reflect.ownKeys(i)){if(o==="__proto__")continue;let c=e.keyType._zod.run({value:o,issues:[]},r);if(c instanceof Promise)throw new Error("Async schemas not supported in object keys currently");if(typeof o=="string"&&ki.test(o)&&c.issues.length){const d=e.keyType._zod.run({value:Number(o),issues:[]},r);if(d instanceof Promise)throw new Error("Async schemas not supported in object keys currently");d.issues.length===0&&(c=d)}if(c.issues.length){e.mode==="loose"?s.value[o]=i[o]:s.issues.push({code:"invalid_key",origin:"record",issues:c.issues.map(d=>ze(d,r,Ue())),input:o,path:[o],inst:t});continue}const u=e.valueType._zod.run({value:i[o],issues:[]},r);u instanceof Promise?n.push(u.then(d=>{d.issues.length&&s.issues.push(...et(o,d.issues)),s.value[c.value]=d.value})):(u.issues.length&&s.issues.push(...et(o,u.issues)),s.value[c.value]=u.value)}}return n.length?Promise.all(n).then(()=>s):s}}),Xo=l("$ZodEnum",(t,e)=>{P.init(t,e);const s=fi(e.entries),r=new Set(s);t._zod.values=r,t._zod.pattern=new RegExp(`^(${s.filter(i=>oa.has(typeof i)).map(i=>typeof i=="string"?ft(i):i.toString()).join("|")})$`),t._zod.parse=(i,n)=>{const a=i.value;return r.has(a)||i.issues.push({code:"invalid_value",values:s,input:a,inst:t}),i}}),Ko=l("$ZodLiteral",(t,e)=>{if(P.init(t,e),e.values.length===0)throw new Error("Cannot create literal schema with no valid values");const s=new Set(e.values);t._zod.values=s,t._zod.pattern=new RegExp(`^(${e.values.map(r=>typeof r=="string"?ft(r):r?ft(r.toString()):String(r)).join("|")})$`),t._zod.parse=(r,i)=>{const n=r.value;return s.has(n)||r.issues.push({code:"invalid_value",values:e.values,input:n,inst:t}),r}}),Yo=l("$ZodTransform",(t,e)=>{P.init(t,e),t._zod.parse=(s,r)=>{if(r.direction==="backward")throw new li(t.constructor.name);const i=e.transform(s.value,s);if(r.async)return(i instanceof Promise?i:Promise.resolve(i)).then(n=>(s.value=n,s));if(i instanceof Promise)throw new nt;return s.value=i,s}});function fr(t,e){return t.issues.length&&e===void 0?{issues:[],value:void 0}:t}const Oi=l("$ZodOptional",(t,e)=>{P.init(t,e),t._zod.optin="optional",t._zod.optout="optional",I(t._zod,"values",()=>e.innerType._zod.values?new Set([...e.innerType._zod.values,void 0]):void 0),I(t._zod,"pattern",()=>{const s=e.innerType._zod.pattern;return s?new RegExp(`^(${Os(s.source)})?$`):void 0}),t._zod.parse=(s,r)=>{if(e.innerType._zod.optin==="optional"){const i=e.innerType._zod.run(s,r);return i instanceof Promise?i.then(n=>fr(n,s.value)):fr(i,s.value)}return s.value===void 0?s:e.innerType._zod.run(s,r)}}),Qo=l("$ZodExactOptional",(t,e)=>{Oi.init(t,e),I(t._zod,"values",()=>e.innerType._zod.values),I(t._zod,"pattern",()=>e.innerType._zod.pattern),t._zod.parse=(s,r)=>e.innerType._zod.run(s,r)}),ec=l("$ZodNullable",(t,e)=>{P.init(t,e),I(t._zod,"optin",()=>e.innerType._zod.optin),I(t._zod,"optout",()=>e.innerType._zod.optout),I(t._zod,"pattern",()=>{const s=e.innerType._zod.pattern;return s?new RegExp(`^(${Os(s.source)}|null)$`):void 0}),I(t._zod,"values",()=>e.innerType._zod.values?new Set([...e.innerType._zod.values,null]):void 0),t._zod.parse=(s,r)=>s.value===null?s:e.innerType._zod.run(s,r)}),tc=l("$ZodDefault",(t,e)=>{P.init(t,e),t._zod.optin="optional",I(t._zod,"values",()=>e.innerType._zod.values),t._zod.parse=(s,r)=>{if(r.direction==="backward")return e.innerType._zod.run(s,r);if(s.value===void 0)return s.value=e.defaultValue,s;const i=e.innerType._zod.run(s,r);return i instanceof Promise?i.then(n=>pr(n,e)):pr(i,e)}});function pr(t,e){return t.value===void 0&&(t.value=e.defaultValue),t}const sc=l("$ZodPrefault",(t,e)=>{P.init(t,e),t._zod.optin="optional",I(t._zod,"values",()=>e.innerType._zod.values),t._zod.parse=(s,r)=>(r.direction==="backward"||s.value===void 0&&(s.value=e.defaultValue),e.innerType._zod.run(s,r))}),rc=l("$ZodNonOptional",(t,e)=>{P.init(t,e),I(t._zod,"values",()=>{const s=e.innerType._zod.values;return s?new Set([...s].filter(r=>r!==void 0)):void 0}),t._zod.parse=(s,r)=>{const i=e.innerType._zod.run(s,r);return i instanceof Promise?i.then(n=>wr(n,t)):wr(i,t)}});function wr(t,e){return!t.issues.length&&t.value===void 0&&t.issues.push({code:"invalid_type",expected:"nonoptional",input:t.value,inst:e}),t}const ic=l("$ZodCatch",(t,e)=>{P.init(t,e),I(t._zod,"optin",()=>e.innerType._zod.optin),I(t._zod,"optout",()=>e.innerType._zod.optout),I(t._zod,"values",()=>e.innerType._zod.values),t._zod.parse=(s,r)=>{if(r.direction==="backward")return e.innerType._zod.run(s,r);const i=e.innerType._zod.run(s,r);return i instanceof Promise?i.then(n=>(s.value=n.value,n.issues.length&&(s.value=e.catchValue({...s,error:{issues:n.issues.map(a=>ze(a,r,Ue()))},input:s.value}),s.issues=[]),s)):(s.value=i.value,i.issues.length&&(s.value=e.catchValue({...s,error:{issues:i.issues.map(n=>ze(n,r,Ue()))},input:s.value}),s.issues=[]),s)}}),nc=l("$ZodPipe",(t,e)=>{P.init(t,e),I(t._zod,"values",()=>e.in._zod.values),I(t._zod,"optin",()=>e.in._zod.optin),I(t._zod,"optout",()=>e.out._zod.optout),I(t._zod,"propValues",()=>e.in._zod.propValues),t._zod.parse=(s,r)=>{if(r.direction==="backward"){const n=e.out._zod.run(s,r);return n instanceof Promise?n.then(a=>$t(a,e.in,r)):$t(n,e.in,r)}const i=e.in._zod.run(s,r);return i instanceof Promise?i.then(n=>$t(n,e.out,r)):$t(i,e.out,r)}});function $t(t,e,s){return t.issues.length?(t.aborted=!0,t):e._zod.run({value:t.value,issues:t.issues},s)}const ac=l("$ZodReadonly",(t,e)=>{P.init(t,e),I(t._zod,"propValues",()=>e.innerType._zod.propValues),I(t._zod,"values",()=>e.innerType._zod.values),I(t._zod,"optin",()=>e.innerType?._zod?.optin),I(t._zod,"optout",()=>e.innerType?._zod?.optout),t._zod.parse=(s,r)=>{if(r.direction==="backward")return e.innerType._zod.run(s,r);const i=e.innerType._zod.run(s,r);return i instanceof Promise?i.then(mr):mr(i)}});function mr(t){return t.value=Object.freeze(t.value),t}const oc=l("$ZodCustom",(t,e)=>{G.init(t,e),P.init(t,e),t._zod.parse=(s,r)=>s,t._zod.check=s=>{const r=s.value,i=e.fn(r);if(i instanceof Promise)return i.then(n=>br(n,s,r,t));br(i,s,r,t)}});function br(t,e,s,r){if(!t){const i={code:"custom",input:s,inst:r,path:[...r._zod.def.path??[]],continue:!r._zod.def.abort};r._zod.def.params&&(i.params=r._zod.def.params),e.issues.push(Ot(i))}}var gr;class cc{constructor(){this._map=new WeakMap,this._idmap=new Map}add(e,...s){const r=s[0];return this._map.set(e,r),r&&typeof r=="object"&&"id"in r&&this._idmap.set(r.id,e),this}clear(){return this._map=new WeakMap,this._idmap=new Map,this}remove(e){const s=this._map.get(e);return s&&typeof s=="object"&&"id"in s&&this._idmap.delete(s.id),this._map.delete(e),this}get(e){const s=e._zod.parent;if(s){const r={...this.get(s)??{}};delete r.id;const i={...r,...this._map.get(e)};return Object.keys(i).length?i:void 0}return this._map.get(e)}has(e){return this._map.has(e)}}function uc(){return new cc}(gr=globalThis).__zod_globalRegistry??(gr.__zod_globalRegistry=uc());const yt=globalThis.__zod_globalRegistry;function dc(t,e){return new t({type:"string",...b(e)})}function lc(t,e){return new t({type:"string",format:"email",check:"string_format",abort:!1,...b(e)})}function yr(t,e){return new t({type:"string",format:"guid",check:"string_format",abort:!1,...b(e)})}function hc(t,e){return new t({type:"string",format:"uuid",check:"string_format",abort:!1,...b(e)})}function fc(t,e){return new t({type:"string",format:"uuid",check:"string_format",abort:!1,version:"v4",...b(e)})}function pc(t,e){return new t({type:"string",format:"uuid",check:"string_format",abort:!1,version:"v6",...b(e)})}function wc(t,e){return new t({type:"string",format:"uuid",check:"string_format",abort:!1,version:"v7",...b(e)})}function mc(t,e){return new t({type:"string",format:"url",check:"string_format",abort:!1,...b(e)})}function bc(t,e){return new t({type:"string",format:"emoji",check:"string_format",abort:!1,...b(e)})}function gc(t,e){return new t({type:"string",format:"nanoid",check:"string_format",abort:!1,...b(e)})}function yc(t,e){return new t({type:"string",format:"cuid",check:"string_format",abort:!1,...b(e)})}function vc(t,e){return new t({type:"string",format:"cuid2",check:"string_format",abort:!1,...b(e)})}function _c(t,e){return new t({type:"string",format:"ulid",check:"string_format",abort:!1,...b(e)})}function kc(t,e){return new t({type:"string",format:"xid",check:"string_format",abort:!1,...b(e)})}function Ec(t,e){return new t({type:"string",format:"ksuid",check:"string_format",abort:!1,...b(e)})}function Ic(t,e){return new t({type:"string",format:"ipv4",check:"string_format",abort:!1,...b(e)})}function Sc(t,e){return new t({type:"string",format:"ipv6",check:"string_format",abort:!1,...b(e)})}function Ac(t,e){return new t({type:"string",format:"cidrv4",check:"string_format",abort:!1,...b(e)})}function xc(t,e){return new t({type:"string",format:"cidrv6",check:"string_format",abort:!1,...b(e)})}function Tc(t,e){return new t({type:"string",format:"base64",check:"string_format",abort:!1,...b(e)})}function Rc(t,e){return new t({type:"string",format:"base64url",check:"string_format",abort:!1,...b(e)})}function Pc(t,e){return new t({type:"string",format:"e164",check:"string_format",abort:!1,...b(e)})}function Oc(t,e){return new t({type:"string",format:"jwt",check:"string_format",abort:!1,...b(e)})}function Nc(t,e){return new t({type:"string",format:"datetime",check:"string_format",offset:!1,local:!1,precision:null,...b(e)})}function Uc(t,e){return new t({type:"string",format:"date",check:"string_format",...b(e)})}function zc(t,e){return new t({type:"string",format:"time",check:"string_format",precision:null,...b(e)})}function qc(t,e){return new t({type:"string",format:"duration",check:"string_format",...b(e)})}function $c(t,e){return new t({type:"number",checks:[],...b(e)})}function Cc(t,e){return new t({type:"number",check:"number_format",abort:!1,format:"safeint",...b(e)})}function Dc(t,e){return new t({type:"boolean",...b(e)})}function Fc(t){return new t({type:"unknown"})}function Mc(t,e){return new t({type:"never",...b(e)})}function vr(t,e){return new Ii({check:"less_than",...b(e),value:t,inclusive:!1})}function os(t,e){return new Ii({check:"less_than",...b(e),value:t,inclusive:!0})}function _r(t,e){return new Si({check:"greater_than",...b(e),value:t,inclusive:!1})}function cs(t,e){return new Si({check:"greater_than",...b(e),value:t,inclusive:!0})}function kr(t,e){return new eo({check:"multiple_of",...b(e),value:t})}function Ni(t,e){return new so({check:"max_length",...b(e),maximum:t})}function Zt(t,e){return new ro({check:"min_length",...b(e),minimum:t})}function Ui(t,e){return new io({check:"length_equals",...b(e),length:t})}function Lc(t,e){return new no({check:"string_format",format:"regex",...b(e),pattern:t})}function Bc(t){return new ao({check:"string_format",format:"lowercase",...b(t)})}function Vc(t){return new oo({check:"string_format",format:"uppercase",...b(t)})}function jc(t,e){return new co({check:"string_format",format:"includes",...b(e),includes:t})}function Gc(t,e){return new uo({check:"string_format",format:"starts_with",...b(e),prefix:t})}function Zc(t,e){return new lo({check:"string_format",format:"ends_with",...b(e),suffix:t})}function mt(t){return new ho({check:"overwrite",tx:t})}function Wc(t){return mt(e=>e.normalize(t))}function Hc(){return mt(t=>t.trim())}function Jc(){return mt(t=>t.toLowerCase())}function Xc(){return mt(t=>t.toUpperCase())}function Kc(){return mt(t=>na(t))}function Yc(t,e,s){return new t({type:"array",element:e,...b(s)})}function Qc(t,e,s){return new t({type:"custom",check:"custom",fn:e,...b(s)})}function eu(t){const e=tu(s=>(s.addIssue=r=>{if(typeof r=="string")s.issues.push(Ot(r,s.value,e._zod.def));else{const i=r;i.fatal&&(i.continue=!1),i.code??(i.code="custom"),i.input??(i.input=s.value),i.inst??(i.inst=e),i.continue??(i.continue=!e._zod.def.abort),s.issues.push(Ot(i))}},t(s.value,s)));return e}function tu(t,e){const s=new G({check:"custom",...b(e)});return s._zod.check=t,s}function zi(t){let e=t?.target??"draft-2020-12";return e==="draft-4"&&(e="draft-04"),e==="draft-7"&&(e="draft-07"),{processors:t.processors??{},metadataRegistry:t?.metadata??yt,target:e,unrepresentable:t?.unrepresentable??"throw",override:t?.override??(()=>{}),io:t?.io??"output",counter:0,seen:new Map,cycles:t?.cycles??"ref",reused:t?.reused??"inline",external:t?.external??void 0}}function $(t,e,s={path:[],schemaPath:[]}){var r;const i=t._zod.def,n=e.seen.get(t);if(n)return n.count++,s.schemaPath.includes(t)&&(n.cycle=s.path),n.schema;const a={schema:{},count:1,cycle:void 0,path:s.path};e.seen.set(t,a);const o=t._zod.toJSONSchema?.();if(o)a.schema=o;else{const u={...s,schemaPath:[...s.schemaPath,t],path:s.path};if(t._zod.processJSONSchema)t._zod.processJSONSchema(e,a.schema,u);else{const h=a.schema,p=e.processors[i.type];if(!p)throw new Error(`[toJSONSchema]: Non-representable type encountered: ${i.type}`);p(t,e,h,u)}const d=t._zod.parent;d&&(a.ref||(a.ref=d),$(d,e,u),e.seen.get(d).isParent=!0)}const c=e.metadataRegistry.get(t);return c&&Object.assign(a.schema,c),e.io==="input"&&L(t)&&(delete a.schema.examples,delete a.schema.default),e.io==="input"&&a.schema._prefault&&((r=a.schema).default??(r.default=a.schema._prefault)),delete a.schema._prefault,e.seen.get(t).schema}function qi(t,e){const s=t.seen.get(e);if(!s)throw new Error("Unprocessed schema. This is a bug in Zod.");const r=new Map;for(const a of t.seen.entries()){const o=t.metadataRegistry.get(a[0])?.id;if(o){const c=r.get(o);if(c&&c!==a[0])throw new Error(`Duplicate schema id "${o}" detected during JSON Schema conversion. Two different schemas cannot share the same id when converted together.`);r.set(o,a[0])}}const i=a=>{const o=t.target==="draft-2020-12"?"$defs":"definitions";if(t.external){const d=t.external.registry.get(a[0])?.id,h=t.external.uri??(w=>w);if(d)return{ref:h(d)};const p=a[1].defId??a[1].schema.id??`schema${t.counter++}`;return a[1].defId=p,{defId:p,ref:`${h("__shared")}#/${o}/${p}`}}if(a[1]===s)return{ref:"#"};const c=`#/${o}/`,u=a[1].schema.id??`__schema${t.counter++}`;return{defId:u,ref:c+u}},n=a=>{if(a[1].schema.$ref)return;const o=a[1],{ref:c,defId:u}=i(a);o.def={...o.schema},u&&(o.defId=u);const d=o.schema;for(const h in d)delete d[h];d.$ref=c};if(t.cycles==="throw")for(const a of t.seen.entries()){const o=a[1];if(o.cycle)throw new Error(`Cycle detected: #/${o.cycle?.join("/")}/<root>

Set the \`cycles\` parameter to \`"ref"\` to resolve cyclical schemas with defs.`)}for(const a of t.seen.entries()){const o=a[1];if(e===a[0]){n(a);continue}if(t.external){const c=t.external.registry.get(a[0])?.id;if(e!==a[0]&&c){n(a);continue}}if(t.metadataRegistry.get(a[0])?.id){n(a);continue}if(o.cycle){n(a);continue}if(o.count>1&&t.reused==="ref"){n(a);continue}}}function $i(t,e){const s=t.seen.get(e);if(!s)throw new Error("Unprocessed schema. This is a bug in Zod.");const r=a=>{const o=t.seen.get(a);if(o.ref===null)return;const c=o.def??o.schema,u={...c},d=o.ref;if(o.ref=null,d){r(d);const p=t.seen.get(d),w=p.schema;if(w.$ref&&(t.target==="draft-07"||t.target==="draft-04"||t.target==="openapi-3.0")?(c.allOf=c.allOf??[],c.allOf.push(w)):Object.assign(c,w),Object.assign(c,u),a._zod.parent===d)for(const y in c)y==="$ref"||y==="allOf"||y in u||delete c[y];if(w.$ref&&p.def)for(const y in c)y==="$ref"||y==="allOf"||y in p.def&&JSON.stringify(c[y])===JSON.stringify(p.def[y])&&delete c[y]}const h=a._zod.parent;if(h&&h!==d){r(h);const p=t.seen.get(h);if(p?.schema.$ref&&(c.$ref=p.schema.$ref,p.def))for(const w in c)w==="$ref"||w==="allOf"||w in p.def&&JSON.stringify(c[w])===JSON.stringify(p.def[w])&&delete c[w]}t.override({zodSchema:a,jsonSchema:c,path:o.path??[]})};for(const a of[...t.seen.entries()].reverse())r(a[0]);const i={};if(t.target==="draft-2020-12"?i.$schema="https://json-schema.org/draft/2020-12/schema":t.target==="draft-07"?i.$schema="http://json-schema.org/draft-07/schema#":t.target==="draft-04"?i.$schema="http://json-schema.org/draft-04/schema#":t.target,t.external?.uri){const a=t.external.registry.get(e)?.id;if(!a)throw new Error("Schema is missing an `id` property");i.$id=t.external.uri(a)}Object.assign(i,s.def??s.schema);const n=t.external?.defs??{};for(const a of t.seen.entries()){const o=a[1];o.def&&o.defId&&(n[o.defId]=o.def)}t.external||Object.keys(n).length>0&&(t.target==="draft-2020-12"?i.$defs=n:i.definitions=n);try{const a=JSON.parse(JSON.stringify(i));return Object.defineProperty(a,"~standard",{value:{...e["~standard"],jsonSchema:{input:Wt(e,"input",t.processors),output:Wt(e,"output",t.processors)}},enumerable:!1,writable:!1}),a}catch{throw new Error("Error converting schema to JSON.")}}function L(t,e){const s=e??{seen:new Set};if(s.seen.has(t))return!1;s.seen.add(t);const r=t._zod.def;if(r.type==="transform")return!0;if(r.type==="array")return L(r.element,s);if(r.type==="set")return L(r.valueType,s);if(r.type==="lazy")return L(r.getter(),s);if(r.type==="promise"||r.type==="optional"||r.type==="nonoptional"||r.type==="nullable"||r.type==="readonly"||r.type==="default"||r.type==="prefault")return L(r.innerType,s);if(r.type==="intersection")return L(r.left,s)||L(r.right,s);if(r.type==="record"||r.type==="map")return L(r.keyType,s)||L(r.valueType,s);if(r.type==="pipe")return L(r.in,s)||L(r.out,s);if(r.type==="object"){for(const i in r.shape)if(L(r.shape[i],s))return!0;return!1}if(r.type==="union"){for(const i of r.options)if(L(i,s))return!0;return!1}if(r.type==="tuple"){for(const i of r.items)if(L(i,s))return!0;return!!(r.rest&&L(r.rest,s))}return!1}const su=(t,e={})=>s=>{const r=zi({...s,processors:e});return $(t,r),qi(r,t),$i(r,t)},Wt=(t,e,s={})=>r=>{const{libraryOptions:i,target:n}=r??{},a=zi({...i??{},target:n,io:e,processors:s});return $(t,a),qi(a,t),$i(a,t)},ru={guid:"uuid",url:"uri",datetime:"date-time",json_string:"json-string",regex:""},iu=(t,e,s,r)=>{const i=s;i.type="string";const{minimum:n,maximum:a,format:o,patterns:c,contentEncoding:u}=t._zod.bag;if(typeof n=="number"&&(i.minLength=n),typeof a=="number"&&(i.maxLength=a),o&&(i.format=ru[o]??o,i.format===""&&delete i.format,o==="time"&&delete i.format),u&&(i.contentEncoding=u),c&&c.size>0){const d=[...c];d.length===1?i.pattern=d[0].source:d.length>1&&(i.allOf=[...d.map(h=>({...e.target==="draft-07"||e.target==="draft-04"||e.target==="openapi-3.0"?{type:"string"}:{},pattern:h.source}))])}},nu=(t,e,s,r)=>{const i=s,{minimum:n,maximum:a,format:o,multipleOf:c,exclusiveMaximum:u,exclusiveMinimum:d}=t._zod.bag;typeof o=="string"&&o.includes("int")?i.type="integer":i.type="number",typeof d=="number"&&(e.target==="draft-04"||e.target==="openapi-3.0"?(i.minimum=d,i.exclusiveMinimum=!0):i.exclusiveMinimum=d),typeof n=="number"&&(i.minimum=n,typeof d=="number"&&e.target!=="draft-04"&&(d>=n?delete i.minimum:delete i.exclusiveMinimum)),typeof u=="number"&&(e.target==="draft-04"||e.target==="openapi-3.0"?(i.maximum=u,i.exclusiveMaximum=!0):i.exclusiveMaximum=u),typeof a=="number"&&(i.maximum=a,typeof u=="number"&&e.target!=="draft-04"&&(u<=a?delete i.maximum:delete i.exclusiveMaximum)),typeof c=="number"&&(i.multipleOf=c)},au=(t,e,s,r)=>{s.type="boolean"},ou=(t,e,s,r)=>{s.not={}},cu=(t,e,s,r)=>{},uu=(t,e,s,r)=>{const i=t._zod.def,n=fi(i.entries);n.every(a=>typeof a=="number")&&(s.type="number"),n.every(a=>typeof a=="string")&&(s.type="string"),s.enum=n},du=(t,e,s,r)=>{const i=t._zod.def,n=[];for(const a of i.values)if(a===void 0){if(e.unrepresentable==="throw")throw new Error("Literal `undefined` cannot be represented in JSON Schema")}else if(typeof a=="bigint"){if(e.unrepresentable==="throw")throw new Error("BigInt literals cannot be represented in JSON Schema");n.push(Number(a))}else n.push(a);if(n.length!==0)if(n.length===1){const a=n[0];s.type=a===null?"null":typeof a,e.target==="draft-04"||e.target==="openapi-3.0"?s.enum=[a]:s.const=a}else n.every(a=>typeof a=="number")&&(s.type="number"),n.every(a=>typeof a=="string")&&(s.type="string"),n.every(a=>typeof a=="boolean")&&(s.type="boolean"),n.every(a=>a===null)&&(s.type="null"),s.enum=n},lu=(t,e,s,r)=>{if(e.unrepresentable==="throw")throw new Error("Custom types cannot be represented in JSON Schema")},hu=(t,e,s,r)=>{if(e.unrepresentable==="throw")throw new Error("Transforms cannot be represented in JSON Schema")},fu=(t,e,s,r)=>{const i=s,n=t._zod.def,{minimum:a,maximum:o}=t._zod.bag;typeof a=="number"&&(i.minItems=a),typeof o=="number"&&(i.maxItems=o),i.type="array",i.items=$(n.element,e,{...r,path:[...r.path,"items"]})},pu=(t,e,s,r)=>{const i=s,n=t._zod.def;i.type="object",i.properties={};const a=n.shape;for(const u in a)i.properties[u]=$(a[u],e,{...r,path:[...r.path,"properties",u]});const o=new Set(Object.keys(a)),c=new Set([...o].filter(u=>{const d=n.shape[u]._zod;return e.io==="input"?d.optin===void 0:d.optout===void 0}));c.size>0&&(i.required=Array.from(c)),n.catchall?._zod.def.type==="never"?i.additionalProperties=!1:n.catchall?n.catchall&&(i.additionalProperties=$(n.catchall,e,{...r,path:[...r.path,"additionalProperties"]})):e.io==="output"&&(i.additionalProperties=!1)},wu=(t,e,s,r)=>{const i=t._zod.def,n=i.inclusive===!1,a=i.options.map((o,c)=>$(o,e,{...r,path:[...r.path,n?"oneOf":"anyOf",c]}));n?s.oneOf=a:s.anyOf=a},mu=(t,e,s,r)=>{const i=t._zod.def,n=$(i.left,e,{...r,path:[...r.path,"allOf",0]}),a=$(i.right,e,{...r,path:[...r.path,"allOf",1]}),o=u=>"allOf"in u&&Object.keys(u).length===1,c=[...o(n)?n.allOf:[n],...o(a)?a.allOf:[a]];s.allOf=c},bu=(t,e,s,r)=>{const i=s,n=t._zod.def;i.type="object";const a=n.keyType,o=a._zod.bag?.patterns;if(n.mode==="loose"&&o&&o.size>0){const u=$(n.valueType,e,{...r,path:[...r.path,"patternProperties","*"]});i.patternProperties={};for(const d of o)i.patternProperties[d.source]=u}else(e.target==="draft-07"||e.target==="draft-2020-12")&&(i.propertyNames=$(n.keyType,e,{...r,path:[...r.path,"propertyNames"]})),i.additionalProperties=$(n.valueType,e,{...r,path:[...r.path,"additionalProperties"]});const c=a._zod.values;if(c){const u=[...c].filter(d=>typeof d=="string"||typeof d=="number");u.length>0&&(i.required=u)}},gu=(t,e,s,r)=>{const i=t._zod.def,n=$(i.innerType,e,r),a=e.seen.get(t);e.target==="openapi-3.0"?(a.ref=i.innerType,s.nullable=!0):s.anyOf=[n,{type:"null"}]},yu=(t,e,s,r)=>{const i=t._zod.def;$(i.innerType,e,r);const n=e.seen.get(t);n.ref=i.innerType},vu=(t,e,s,r)=>{const i=t._zod.def;$(i.innerType,e,r);const n=e.seen.get(t);n.ref=i.innerType,s.default=JSON.parse(JSON.stringify(i.defaultValue))},_u=(t,e,s,r)=>{const i=t._zod.def;$(i.innerType,e,r);const n=e.seen.get(t);n.ref=i.innerType,e.io==="input"&&(s._prefault=JSON.parse(JSON.stringify(i.defaultValue)))},ku=(t,e,s,r)=>{const i=t._zod.def;$(i.innerType,e,r);const n=e.seen.get(t);n.ref=i.innerType;let a;try{a=i.catchValue(void 0)}catch{throw new Error("Dynamic catch values are not supported in JSON Schema")}s.default=a},Eu=(t,e,s,r)=>{const i=t._zod.def,n=e.io==="input"?i.in._zod.def.type==="transform"?i.out:i.in:i.out;$(n,e,r);const a=e.seen.get(t);a.ref=n},Iu=(t,e,s,r)=>{const i=t._zod.def;$(i.innerType,e,r);const n=e.seen.get(t);n.ref=i.innerType,s.readOnly=!0},Ci=(t,e,s,r)=>{const i=t._zod.def;$(i.innerType,e,r);const n=e.seen.get(t);n.ref=i.innerType},Su=l("ZodISODateTime",(t,e)=>{Ao.init(t,e),N.init(t,e)});function Au(t){return Nc(Su,t)}const xu=l("ZodISODate",(t,e)=>{xo.init(t,e),N.init(t,e)});function Tu(t){return Uc(xu,t)}const Ru=l("ZodISOTime",(t,e)=>{To.init(t,e),N.init(t,e)});function Pu(t){return zc(Ru,t)}const Ou=l("ZodISODuration",(t,e)=>{Ro.init(t,e),N.init(t,e)});function Nu(t){return qc(Ou,t)}const Uu=(t,e)=>{bi.init(t,e),t.name="ZodError",Object.defineProperties(t,{format:{value:s=>ga(t,s)},flatten:{value:s=>ba(t,s)},addIssue:{value:s=>{t.issues.push(s),t.message=JSON.stringify(t.issues,ys,2)}},addIssues:{value:s=>{t.issues.push(...s),t.message=JSON.stringify(t.issues,ys,2)}},isEmpty:{get(){return t.issues.length===0}}})},se=l("ZodError",Uu,{Parent:Error}),zu=Us(se),qu=zs(se),$u=es(se),Cu=ts(se),Du=_a(se),Fu=ka(se),Mu=Ea(se),Lu=Ia(se),Bu=Sa(se),Vu=Aa(se),ju=xa(se),Gu=Ta(se),O=l("ZodType",(t,e)=>(P.init(t,e),Object.assign(t["~standard"],{jsonSchema:{input:Wt(t,"input"),output:Wt(t,"output")}}),t.toJSONSchema=su(t,{}),t.def=e,t.type=e.type,Object.defineProperty(t,"_def",{value:e}),t.check=(...s)=>t.clone($e(e,{checks:[...e.checks??[],...s.map(r=>typeof r=="function"?{_zod:{check:r,def:{check:"custom"},onattach:[]}}:r)]}),{parent:!0}),t.with=t.check,t.clone=(s,r)=>Ce(t,s,r),t.brand=()=>t,t.register=((s,r)=>(s.add(t,r),t)),t.parse=(s,r)=>zu(t,s,r,{callee:t.parse}),t.safeParse=(s,r)=>$u(t,s,r),t.parseAsync=async(s,r)=>qu(t,s,r,{callee:t.parseAsync}),t.safeParseAsync=async(s,r)=>Cu(t,s,r),t.spa=t.safeParseAsync,t.encode=(s,r)=>Du(t,s,r),t.decode=(s,r)=>Fu(t,s,r),t.encodeAsync=async(s,r)=>Mu(t,s,r),t.decodeAsync=async(s,r)=>Lu(t,s,r),t.safeEncode=(s,r)=>Bu(t,s,r),t.safeDecode=(s,r)=>Vu(t,s,r),t.safeEncodeAsync=async(s,r)=>ju(t,s,r),t.safeDecodeAsync=async(s,r)=>Gu(t,s,r),t.refine=(s,r)=>t.check(Ld(s,r)),t.superRefine=s=>t.check(Bd(s)),t.overwrite=s=>t.check(mt(s)),t.optional=()=>xr(t),t.exactOptional=()=>Td(t),t.nullable=()=>Tr(t),t.nullish=()=>xr(Tr(t)),t.nonoptional=s=>zd(t,s),t.array=()=>Re(t),t.or=s=>bd([t,s]),t.and=s=>_d(t,s),t.transform=s=>Rr(t,Ad(s)),t.default=s=>Od(t,s),t.prefault=s=>Ud(t,s),t.catch=s=>$d(t,s),t.pipe=s=>Rr(t,s),t.readonly=()=>Fd(t),t.describe=s=>{const r=t.clone();return yt.add(r,{description:s}),r},Object.defineProperty(t,"description",{get(){return yt.get(t)?.description},configurable:!0}),t.meta=(...s)=>{if(s.length===0)return yt.get(t);const r=t.clone();return yt.add(r,s[0]),r},t.isOptional=()=>t.safeParse(void 0).success,t.isNullable=()=>t.safeParse(null).success,t.apply=s=>s(t),t)),Di=l("_ZodString",(t,e)=>{qs.init(t,e),O.init(t,e),t._zod.processJSONSchema=(r,i,n)=>iu(t,r,i);const s=t._zod.bag;t.format=s.format??null,t.minLength=s.minimum??null,t.maxLength=s.maximum??null,t.regex=(...r)=>t.check(Lc(...r)),t.includes=(...r)=>t.check(jc(...r)),t.startsWith=(...r)=>t.check(Gc(...r)),t.endsWith=(...r)=>t.check(Zc(...r)),t.min=(...r)=>t.check(Zt(...r)),t.max=(...r)=>t.check(Ni(...r)),t.length=(...r)=>t.check(Ui(...r)),t.nonempty=(...r)=>t.check(Zt(1,...r)),t.lowercase=r=>t.check(Bc(r)),t.uppercase=r=>t.check(Vc(r)),t.trim=()=>t.check(Hc()),t.normalize=(...r)=>t.check(Wc(...r)),t.toLowerCase=()=>t.check(Jc()),t.toUpperCase=()=>t.check(Xc()),t.slugify=()=>t.check(Kc())}),Zu=l("ZodString",(t,e)=>{qs.init(t,e),Di.init(t,e),t.email=s=>t.check(lc(Wu,s)),t.url=s=>t.check(mc(Hu,s)),t.jwt=s=>t.check(Oc(ud,s)),t.emoji=s=>t.check(bc(Ju,s)),t.guid=s=>t.check(yr(Er,s)),t.uuid=s=>t.check(hc(Ct,s)),t.uuidv4=s=>t.check(fc(Ct,s)),t.uuidv6=s=>t.check(pc(Ct,s)),t.uuidv7=s=>t.check(wc(Ct,s)),t.nanoid=s=>t.check(gc(Xu,s)),t.guid=s=>t.check(yr(Er,s)),t.cuid=s=>t.check(yc(Ku,s)),t.cuid2=s=>t.check(vc(Yu,s)),t.ulid=s=>t.check(_c(Qu,s)),t.base64=s=>t.check(Tc(ad,s)),t.base64url=s=>t.check(Rc(od,s)),t.xid=s=>t.check(kc(ed,s)),t.ksuid=s=>t.check(Ec(td,s)),t.ipv4=s=>t.check(Ic(sd,s)),t.ipv6=s=>t.check(Sc(rd,s)),t.cidrv4=s=>t.check(Ac(id,s)),t.cidrv6=s=>t.check(xc(nd,s)),t.e164=s=>t.check(Pc(cd,s)),t.datetime=s=>t.check(Au(s)),t.date=s=>t.check(Tu(s)),t.time=s=>t.check(Pu(s)),t.duration=s=>t.check(Nu(s))});function R(t){return dc(Zu,t)}const N=l("ZodStringFormat",(t,e)=>{x.init(t,e),Di.init(t,e)}),Wu=l("ZodEmail",(t,e)=>{bo.init(t,e),N.init(t,e)}),Er=l("ZodGUID",(t,e)=>{wo.init(t,e),N.init(t,e)}),Ct=l("ZodUUID",(t,e)=>{mo.init(t,e),N.init(t,e)}),Hu=l("ZodURL",(t,e)=>{go.init(t,e),N.init(t,e)}),Ju=l("ZodEmoji",(t,e)=>{yo.init(t,e),N.init(t,e)}),Xu=l("ZodNanoID",(t,e)=>{vo.init(t,e),N.init(t,e)}),Ku=l("ZodCUID",(t,e)=>{_o.init(t,e),N.init(t,e)}),Yu=l("ZodCUID2",(t,e)=>{ko.init(t,e),N.init(t,e)}),Qu=l("ZodULID",(t,e)=>{Eo.init(t,e),N.init(t,e)}),ed=l("ZodXID",(t,e)=>{Io.init(t,e),N.init(t,e)}),td=l("ZodKSUID",(t,e)=>{So.init(t,e),N.init(t,e)}),sd=l("ZodIPv4",(t,e)=>{Po.init(t,e),N.init(t,e)}),rd=l("ZodIPv6",(t,e)=>{Oo.init(t,e),N.init(t,e)}),id=l("ZodCIDRv4",(t,e)=>{No.init(t,e),N.init(t,e)}),nd=l("ZodCIDRv6",(t,e)=>{Uo.init(t,e),N.init(t,e)}),ad=l("ZodBase64",(t,e)=>{zo.init(t,e),N.init(t,e)}),od=l("ZodBase64URL",(t,e)=>{$o.init(t,e),N.init(t,e)}),cd=l("ZodE164",(t,e)=>{Co.init(t,e),N.init(t,e)}),ud=l("ZodJWT",(t,e)=>{Fo.init(t,e),N.init(t,e)}),Fi=l("ZodNumber",(t,e)=>{xi.init(t,e),O.init(t,e),t._zod.processJSONSchema=(r,i,n)=>nu(t,r,i),t.gt=(r,i)=>t.check(_r(r,i)),t.gte=(r,i)=>t.check(cs(r,i)),t.min=(r,i)=>t.check(cs(r,i)),t.lt=(r,i)=>t.check(vr(r,i)),t.lte=(r,i)=>t.check(os(r,i)),t.max=(r,i)=>t.check(os(r,i)),t.int=r=>t.check(Ir(r)),t.safe=r=>t.check(Ir(r)),t.positive=r=>t.check(_r(0,r)),t.nonnegative=r=>t.check(cs(0,r)),t.negative=r=>t.check(vr(0,r)),t.nonpositive=r=>t.check(os(0,r)),t.multipleOf=(r,i)=>t.check(kr(r,i)),t.step=(r,i)=>t.check(kr(r,i)),t.finite=()=>t;const s=t._zod.bag;t.minValue=Math.max(s.minimum??Number.NEGATIVE_INFINITY,s.exclusiveMinimum??Number.NEGATIVE_INFINITY)??null,t.maxValue=Math.min(s.maximum??Number.POSITIVE_INFINITY,s.exclusiveMaximum??Number.POSITIVE_INFINITY)??null,t.isInt=(s.format??"").includes("int")||Number.isSafeInteger(s.multipleOf??.5),t.isFinite=!0,t.format=s.format??null});function xe(t){return $c(Fi,t)}const dd=l("ZodNumberFormat",(t,e)=>{Mo.init(t,e),Fi.init(t,e)});function Ir(t){return Cc(dd,t)}const ld=l("ZodBoolean",(t,e)=>{Lo.init(t,e),O.init(t,e),t._zod.processJSONSchema=(s,r,i)=>au(t,s,r)});function Fe(t){return Dc(ld,t)}const hd=l("ZodUnknown",(t,e)=>{Bo.init(t,e),O.init(t,e),t._zod.processJSONSchema=(s,r,i)=>cu()});function Sr(){return Fc(hd)}const fd=l("ZodNever",(t,e)=>{Vo.init(t,e),O.init(t,e),t._zod.processJSONSchema=(s,r,i)=>ou(t,s,r)});function pd(t){return Mc(fd,t)}const wd=l("ZodArray",(t,e)=>{jo.init(t,e),O.init(t,e),t._zod.processJSONSchema=(s,r,i)=>fu(t,s,r,i),t.element=e.element,t.min=(s,r)=>t.check(Zt(s,r)),t.nonempty=s=>t.check(Zt(1,s)),t.max=(s,r)=>t.check(Ni(s,r)),t.length=(s,r)=>t.check(Ui(s,r)),t.unwrap=()=>t.element});function Re(t,e){return Yc(wd,t,e)}const md=l("ZodObject",(t,e)=>{Zo.init(t,e),O.init(t,e),t._zod.processJSONSchema=(s,r,i)=>pu(t,s,r,i),I(t,"shape",()=>e.shape),t.keyof=()=>Ed(Object.keys(t._zod.def.shape)),t.catchall=s=>t.clone({...t._zod.def,catchall:s}),t.passthrough=()=>t.clone({...t._zod.def,catchall:Sr()}),t.loose=()=>t.clone({...t._zod.def,catchall:Sr()}),t.strict=()=>t.clone({...t._zod.def,catchall:pd()}),t.strip=()=>t.clone({...t._zod.def,catchall:void 0}),t.extend=s=>ha(t,s),t.safeExtend=s=>fa(t,s),t.merge=s=>pa(t,s),t.pick=s=>da(t,s),t.omit=s=>la(t,s),t.partial=(...s)=>wa(Li,t,s[0]),t.required=(...s)=>ma(Bi,t,s[0])});function z(t,e){const s={type:"object",shape:t??{},...b(e)};return new md(s)}const Mi=l("ZodUnion",(t,e)=>{Pi.init(t,e),O.init(t,e),t._zod.processJSONSchema=(s,r,i)=>wu(t,s,r,i),t.options=e.options});function bd(t,e){return new Mi({type:"union",options:t,...b(e)})}const gd=l("ZodDiscriminatedUnion",(t,e)=>{Mi.init(t,e),Wo.init(t,e)});function yd(t,e,s){return new gd({type:"union",options:e,discriminator:t,...b(s)})}const vd=l("ZodIntersection",(t,e)=>{Ho.init(t,e),O.init(t,e),t._zod.processJSONSchema=(s,r,i)=>mu(t,s,r,i)});function _d(t,e){return new vd({type:"intersection",left:t,right:e})}const kd=l("ZodRecord",(t,e)=>{Jo.init(t,e),O.init(t,e),t._zod.processJSONSchema=(s,r,i)=>bu(t,s,r,i),t.keyType=e.keyType,t.valueType=e.valueType});function $s(t,e,s){return new kd({type:"record",keyType:t,valueType:e,...b(s)})}const _s=l("ZodEnum",(t,e)=>{Xo.init(t,e),O.init(t,e),t._zod.processJSONSchema=(r,i,n)=>uu(t,r,i),t.enum=e.entries,t.options=Object.values(e.entries);const s=new Set(Object.keys(e.entries));t.extract=(r,i)=>{const n={};for(const a of r)if(s.has(a))n[a]=e.entries[a];else throw new Error(`Key ${a} not found in enum`);return new _s({...e,checks:[],...b(i),entries:n})},t.exclude=(r,i)=>{const n={...e.entries};for(const a of r)if(s.has(a))delete n[a];else throw new Error(`Key ${a} not found in enum`);return new _s({...e,checks:[],...b(i),entries:n})}});function Ed(t,e){const s=Array.isArray(t)?Object.fromEntries(t.map(r=>[r,r])):t;return new _s({type:"enum",entries:s,...b(e)})}const Id=l("ZodLiteral",(t,e)=>{Ko.init(t,e),O.init(t,e),t._zod.processJSONSchema=(s,r,i)=>du(t,s,r),t.values=new Set(e.values),Object.defineProperty(t,"value",{get(){if(e.values.length>1)throw new Error("This schema contains multiple valid literal values. Use `.values` instead.");return e.values[0]}})});function Ar(t,e){return new Id({type:"literal",values:Array.isArray(t)?t:[t],...b(e)})}const Sd=l("ZodTransform",(t,e)=>{Yo.init(t,e),O.init(t,e),t._zod.processJSONSchema=(s,r,i)=>hu(t,s),t._zod.parse=(s,r)=>{if(r.direction==="backward")throw new li(t.constructor.name);s.addIssue=n=>{if(typeof n=="string")s.issues.push(Ot(n,s.value,e));else{const a=n;a.fatal&&(a.continue=!1),a.code??(a.code="custom"),a.input??(a.input=s.value),a.inst??(a.inst=t),s.issues.push(Ot(a))}};const i=e.transform(s.value,s);return i instanceof Promise?i.then(n=>(s.value=n,s)):(s.value=i,s)}});function Ad(t){return new Sd({type:"transform",transform:t})}const Li=l("ZodOptional",(t,e)=>{Oi.init(t,e),O.init(t,e),t._zod.processJSONSchema=(s,r,i)=>Ci(t,s,r,i),t.unwrap=()=>t._zod.def.innerType});function xr(t){return new Li({type:"optional",innerType:t})}const xd=l("ZodExactOptional",(t,e)=>{Qo.init(t,e),O.init(t,e),t._zod.processJSONSchema=(s,r,i)=>Ci(t,s,r,i),t.unwrap=()=>t._zod.def.innerType});function Td(t){return new xd({type:"optional",innerType:t})}const Rd=l("ZodNullable",(t,e)=>{ec.init(t,e),O.init(t,e),t._zod.processJSONSchema=(s,r,i)=>gu(t,s,r,i),t.unwrap=()=>t._zod.def.innerType});function Tr(t){return new Rd({type:"nullable",innerType:t})}const Pd=l("ZodDefault",(t,e)=>{tc.init(t,e),O.init(t,e),t._zod.processJSONSchema=(s,r,i)=>vu(t,s,r,i),t.unwrap=()=>t._zod.def.innerType,t.removeDefault=t.unwrap});function Od(t,e){return new Pd({type:"default",innerType:t,get defaultValue(){return typeof e=="function"?e():wi(e)}})}const Nd=l("ZodPrefault",(t,e)=>{sc.init(t,e),O.init(t,e),t._zod.processJSONSchema=(s,r,i)=>_u(t,s,r,i),t.unwrap=()=>t._zod.def.innerType});function Ud(t,e){return new Nd({type:"prefault",innerType:t,get defaultValue(){return typeof e=="function"?e():wi(e)}})}const Bi=l("ZodNonOptional",(t,e)=>{rc.init(t,e),O.init(t,e),t._zod.processJSONSchema=(s,r,i)=>yu(t,s,r,i),t.unwrap=()=>t._zod.def.innerType});function zd(t,e){return new Bi({type:"nonoptional",innerType:t,...b(e)})}const qd=l("ZodCatch",(t,e)=>{ic.init(t,e),O.init(t,e),t._zod.processJSONSchema=(s,r,i)=>ku(t,s,r,i),t.unwrap=()=>t._zod.def.innerType,t.removeCatch=t.unwrap});function $d(t,e){return new qd({type:"catch",innerType:t,catchValue:typeof e=="function"?e:()=>e})}const Cd=l("ZodPipe",(t,e)=>{nc.init(t,e),O.init(t,e),t._zod.processJSONSchema=(s,r,i)=>Eu(t,s,r,i),t.in=e.in,t.out=e.out});function Rr(t,e){return new Cd({type:"pipe",in:t,out:e})}const Dd=l("ZodReadonly",(t,e)=>{ac.init(t,e),O.init(t,e),t._zod.processJSONSchema=(s,r,i)=>Iu(t,s,r,i),t.unwrap=()=>t._zod.def.innerType});function Fd(t){return new Dd({type:"readonly",innerType:t})}const Md=l("ZodCustom",(t,e)=>{oc.init(t,e),O.init(t,e),t._zod.processJSONSchema=(s,r,i)=>lu(t,s)});function Ld(t,e={}){return Qc(Md,t,e)}function Bd(t){return eu(t)}xe().int().nonnegative().max(255).brand("u8");const V=xe().int().nonnegative().max(Number.MAX_SAFE_INTEGER).brand("u53"),Vi=yd("kind",[z({kind:Ar("legacy")}),z({kind:Ar("cmaf"),timescale:V,trackId:V})]).default({kind:"legacy"}),Vd=z({name:R()}),Pr=z({codec:R(),container:Vi,description:R().optional(),sampleRate:V,numberOfChannels:V,bitrate:V.optional(),jitter:V.optional()}),jd=z({renditions:$s(R(),Pr)}).or(z({track:Vd,config:Pr}).transform(t=>({renditions:{[t.track.name]:t.config}}))),Gd=z({hardware:Re(R()).optional(),software:Re(R()).optional(),unsupported:Re(R()).optional()}),Zd=z({hardware:Re(R()).optional(),software:Re(R()).optional(),unsupported:Re(R()).optional()}),Wd=z({video:Gd.optional(),audio:Zd.optional()}),Nt=z({name:R()}),Hd=z({message:Nt.optional(),typing:Nt.optional()}),ji=z({x:xe().optional(),y:xe().optional(),z:xe().optional(),s:xe().optional()}),Jd=z({initial:ji.optional(),track:Nt.optional(),handle:R().optional(),peers:Nt.optional()});$s(R(),ji);z({name:R().optional(),avatar:R().optional(),audio:Fe().optional(),video:Fe().optional(),typing:Fe().optional(),chat:Fe().optional(),screen:Fe().optional()});const pt={catalog:100,audio:80,video:60},Xd=z({id:R().optional(),name:R().optional(),avatar:R().optional(),color:R().optional()}),Kd=z({name:R()}),Or=z({codec:R(),container:Vi,description:R().optional(),codedWidth:V.optional(),codedHeight:V.optional(),displayAspectWidth:V.optional(),displayAspectHeight:V.optional(),framerate:xe().optional(),bitrate:V.optional(),optimizeForLatency:Fe().optional(),jitter:V.optional()}),Yd=z({renditions:$s(R(),Or),display:z({width:V,height:V}).optional(),rotation:xe().optional(),flip:Fe().optional()}).or(Re(z({track:Kd,config:Or})).transform(t=>{const e=t[0]?.config;return{renditions:Object.fromEntries(t.map(s=>[s.track.name,s.config])),display:e?.displayAspectWidth&&e?.displayAspectHeight?{width:e.displayAspectWidth,height:e.displayAspectHeight}:void 0,rotation:void 0,flip:void 0}})),Qd=z({video:Yd.optional(),audio:jd.optional(),location:Jd.optional(),user:Xd.optional(),chat:Hd.optional(),capabilities:Wd.optional(),preview:Nt.optional()});function el(t){const e=new TextDecoder().decode(t);try{const s=JSON.parse(e);return Qd.parse(s)}catch(s){throw console.warn("invalid catalog",e),s}}async function tl(t){const e=await t.readFrame();if(e)return el(e)}function Gi(t){return t instanceof ArrayBuffer||typeof SharedArrayBuffer<"u"&&t instanceof SharedArrayBuffer}const sl="utf-16",us="utf-16be",Nr="utf-16le",Et="utf-8";function Zi(t,e={}){let s;Gi(t)?s=new DataView(t):s=new DataView(t.buffer,t.byteOffset,t.byteLength);let r=0,{encoding:i}=e;if(!i){const u=s.getUint8(0),d=s.getUint8(1);u==239&&d==187&&s.getUint8(2)==191?(i=Et,r=3):u==254&&d==255?(i=us,r=2):u==255&&d==254?(i=Nr,r=2):i=Et}if(typeof TextDecoder<"u")return new TextDecoder(i).decode(s);const{byteLength:n}=s,a=i!==us;let o="",c;for(;r<n;){switch(i){case Et:if(c=s.getUint8(r),c<128)r++;else if(c>=194&&c<=223)if(r+1<n){const u=s.getUint8(r+1);u>=128&&u<=191?(c=(c&31)<<6|u&63,r+=2):r++}else r++;else if(c>=224&&c<=239)if(r+2<=n-1){const u=s.getUint8(r+1),d=s.getUint8(r+2);u>=128&&u<=191&&d>=128&&d<=191?(c=(c&15)<<12|(u&63)<<6|d&63,r+=3):r++}else r++;else if(c>=240&&c<=244)if(r+3<=n-1){const u=s.getUint8(r+1),d=s.getUint8(r+2),h=s.getUint8(r+3);u>=128&&u<=191&&d>=128&&d<=191&&h>=128&&h<=191?(c=(c&7)<<18|(u&63)<<12|(d&63)<<6|h&63,r+=4):r++}else r++;else r++;break;case us:case sl:case Nr:c=s.getUint16(r,a),r+=2;break}o+=String.fromCodePoint(c)}return o}function rl(t){return new TextEncoder().encode(t)}function il(t){return{writers:t?.writers??{}}}const nl=["dinf","edts","grpl","mdia","meco","mfra","minf","moof","moov","mvex","schi","sinf","stbl","strk","traf","trak","tref","udta","vttc"];function Wi(t){return"boxes"in t||nl.includes(t.type)}const Ur="utf8",qe="uint",Ut="template",zr="string",qr="int",$r="data";var F=class{constructor(t,e){this.writeUint=(s,r)=>{const{dataView:i,cursor:n}=this;switch(r){case 1:i.setUint8(n,s);break;case 2:i.setUint16(n,s);break;case 3:{const a=(s&16776960)>>8,o=s&255;i.setUint16(n,a),i.setUint8(n+2,o);break}case 4:i.setUint32(n,s);break;case 8:{const a=Math.floor(s/Math.pow(2,32)),o=s-a*Math.pow(2,32);i.setUint32(n,a),i.setUint32(n+4,o);break}}this.cursor+=r},this.writeInt=(s,r)=>{const{dataView:i,cursor:n}=this;switch(r){case 1:i.setInt8(n,s);break;case 2:i.setInt16(n,s);break;case 4:i.setInt32(n,s);break;case 8:const a=Math.floor(s/Math.pow(2,32)),o=s-a*Math.pow(2,32);i.setUint32(n,a),i.setUint32(n+4,o);break}this.cursor+=r},this.writeString=s=>{for(let r=0,i=s.length;r<i;r++)this.writeUint(s.charCodeAt(r),1)},this.writeTerminatedString=s=>{if(s.length!==0){for(let r=0,i=s.length;r<i;r++)this.writeUint(s.charCodeAt(r),1);this.writeUint(0,1)}},this.writeUtf8TerminatedString=s=>{const r=rl(s);new Uint8Array(this.dataView.buffer).set(r,this.cursor),this.cursor+=r.length,this.writeUint(0,1)},this.writeBytes=s=>{Array.isArray(s)||(s=[s]);for(const r of s)new Uint8Array(this.dataView.buffer).set(r,this.cursor),this.cursor+=r.length},this.writeArray=(s,r,i,n)=>{const a=r===qe?this.writeUint:r===Ut?this.writeTemplate:this.writeInt;for(let o=0;o<n;o++)a(s[o]??0,i)},this.writeTemplate=(s,r)=>{const i=Math.round(s*Math.pow(2,r===4?16:8));this.writeUint(i,r)},this.writeBoxHeader=(s,r)=>{r>4294967295?(this.writeUint(1,4),this.writeString(s),this.writeUint(r,8)):(this.writeUint(r,4),this.writeString(s))},this.dataView=new DataView(new ArrayBuffer(e)),this.cursor=0,this.writeBoxHeader(t,e)}get buffer(){return this.dataView.buffer}get byteLength(){return this.dataView.byteLength}get byteOffset(){return this.dataView.byteOffset}writeFullBox(t,e){this.writeUint(t,1),this.writeUint(e,3)}};function Hi(t,e){return Array.from(t,s=>ol(s,e))}function Cs(t,e){const s=Hi(t,e);return{bytes:s,size:s.reduce((r,i)=>r+i.byteLength,0)}}function al(t,e){const{bytes:s,size:r}=Cs(t.boxes,e),i=8+r,n=new F(t.type,i);return n.writeBytes(s),n}function ol(t,e){let s=null;if("type"in t){const{type:r}=t,i=e.writers?.[r];if(i?s=i(t,e):Wi(t)?s=al(t,e):"view"in t&&(s=t.view),!s)throw new Error(`No writer found for box type: ${r}`)}if("buffer"in t&&(s=t),!s)throw new Error("Invalid box");return new Uint8Array(s.buffer,s.byteOffset,s.byteLength)}function cl(t,e,s){const r=s>0?s:t.byteLength-(e-t.byteOffset);return new Uint8Array(t.buffer,e,Math.max(r,0))}function ul(t,e,s){let r=NaN;const i=e-t.byteOffset;switch(s){case 1:r=t.getInt8(i);break;case 2:r=t.getInt16(i);break;case 4:r=t.getInt32(i);break;case 8:const n=t.getInt32(i),a=t.getInt32(i+4);r=n*Math.pow(2,32)+a;break}return r}function je(t,e,s){const r=e-t.byteOffset;let i=NaN,n,a;switch(s){case 1:i=t.getUint8(r);break;case 2:i=t.getUint16(r);break;case 3:n=t.getUint16(r),a=t.getUint8(r+2),i=(n<<8)+a;break;case 4:i=t.getUint32(r);break;case 8:n=t.getUint32(r),a=t.getUint32(r+4),i=n*Math.pow(2,32)+a;break}return i}function Cr(t,e,s){let r="";for(let i=0;i<s;i++){const n=je(t,e+i,1);r+=String.fromCharCode(n)}return r}function dl(t,e,s){const r=s/2;return je(t,e,r)+je(t,e+r,r)/Math.pow(2,r)}function ll(t,e){let s="",r=e;for(;r-t.byteOffset<t.byteLength;){const i=je(t,r,1);if(i===0)break;s+=String.fromCharCode(i),r++}return s}function hl(t,e){const s=t.byteLength-(e-t.byteOffset);return s>0?Zi(new DataView(t.buffer,e,s),{encoding:Et}):""}function fl(t,e){const s=t.byteLength-(e-t.byteOffset);let r="";if(s>0){const i=new DataView(t.buffer,e,s);let n=0;for(;n<s&&i.getUint8(n)!==0;n++);r=Zi(new DataView(t.buffer,e,n),{encoding:Et})}return r}var pl=class Ji{constructor(e,s){this.truncated=!1,this.slice=(r,i)=>{const n=new Ji(new DataView(this.dataView.buffer,r,i),this.config),a=this.offset-r,o=i-a;return this.offset+=o,n.jump(a),n},this.read=(r,i=0)=>{const{dataView:n,offset:a}=this;let o,c=i;switch(r){case qe:o=je(n,a,i);break;case qr:o=ul(n,a,i);break;case Ut:o=dl(n,a,i);break;case zr:i===-1?(o=ll(n,a),c=o.length+1):o=Cr(n,a,i);break;case $r:o=cl(n,a,i),c=o.length;break;case Ur:i===-1?(o=fl(n,a),c=o.length+1):o=hl(n,a);break;default:o=-1}return this.offset+=c,o},this.readUint=r=>this.read(qe,r),this.readInt=r=>this.read(qr,r),this.readString=r=>this.read(zr,r),this.readTemplate=r=>this.read(Ut,r),this.readData=r=>this.read($r,r),this.readUtf8=r=>this.read(Ur,r),this.readFullBox=()=>({version:this.readUint(1),flags:this.readUint(3)}),this.readArray=(r,i,n)=>{const a=[];for(let o=0;o<n;o++)a.push(this.read(r,i));return a},this.jump=r=>{this.offset+=r},this.readBox=()=>{const{dataView:r,offset:i}=this;let n=0;const a=je(r,i,4),o=Cr(r,i+4,4),c={size:a,type:o};n+=8,c.size===1&&(c.largesize=je(r,i+n,8),n+=8);const u=c.size===0?this.bytesRemaining:c.largesize??c.size;if(this.cursor+u>r.byteLength)throw this.truncated=!0,new Error("Truncated box");return this.jump(n),o==="uuid"&&(c.usertype=this.readArray("uint",1,16)),c.view=this.slice(i,u),c},this.readBoxes=(r=-1)=>{const i=[];for(const n of this)if(i.push(n),r>0&&i.length>=r)break;return i},this.readEntries=(r,i)=>{const n=[];for(let a=0;a<r;a++)n.push(i());return n},this.dataView=Gi(e)?new DataView(e):e instanceof DataView?e:new DataView(e.buffer,e.byteOffset,e.byteLength),this.offset=this.dataView.byteOffset,this.config=s||{}}get buffer(){return this.dataView.buffer}get byteOffset(){return this.dataView.byteOffset}get byteLength(){return this.dataView.byteLength}get cursor(){return this.offset-this.dataView.byteOffset}get done(){return this.cursor>=this.dataView.byteLength||this.truncated}get bytesRemaining(){return this.dataView.byteLength-this.cursor}*[Symbol.iterator](){const{readers:e={}}=this.config;for(;!this.done;)try{const s=this.readBox(),{type:r,view:i}=s,n=e[r]||e[r.trim()];if(n&&Object.assign(s,n(i,r)),Wi(s)&&!s.boxes){const a=[];for(const o of i)a.push(o);s.boxes=a}yield s}catch(s){if(s instanceof Error&&s.message==="Truncated box")break;throw s}}};function Xi(t,e){const s=[];for(const r of new pl(t,e))s.push(r);return s}function wl(t,e){return Hi(t,il(e))}function ml(t){return{type:"mdat",data:t.readData(-1)}}function bl(t){return{type:"mfhd",...t.readFullBox(),sequenceNumber:t.readUint(4)}}function gl(t){const{version:e,flags:s}=t.readFullBox();return{type:"tfdt",version:e,flags:s,baseMediaDecodeTime:t.readUint(e==1?8:4)}}function yl(t){const{version:e,flags:s}=t.readFullBox();return{type:"tfhd",version:e,flags:s,trackId:t.readUint(4),baseDataOffset:s&1?t.readUint(8):void 0,sampleDescriptionIndex:s&2?t.readUint(4):void 0,defaultSampleDuration:s&8?t.readUint(4):void 0,defaultSampleSize:s&16?t.readUint(4):void 0,defaultSampleFlags:s&32?t.readUint(4):void 0}}function vl(t){const{version:e,flags:s}=t.readFullBox(),r=t.readUint(4);let i,n;s&1&&(i=t.readInt(4)),s&4&&(n=t.readUint(4));const a=t.readEntries(r,()=>{const o={};return s&256&&(o.sampleDuration=t.readUint(4)),s&512&&(o.sampleSize=t.readUint(4)),s&1024&&(o.sampleFlags=t.readUint(4)),s&2048&&(o.sampleCompositionTimeOffset=e===1?t.readInt(4):t.readUint(4)),o});return{type:"trun",version:e,flags:s,sampleCount:r,dataOffset:i,firstSampleFlags:n,samples:a}}function _l(t,e){const s=t.entries.length,{bytes:r,size:i}=Cs(t.entries,e),n=new F("dref",16+i);return n.writeFullBox(t.version,t.flags),n.writeUint(s,4),n.writeBytes(r),n}function kl(t){const e=t.compatibleBrands.length*4,s=new F("ftyp",16+e);s.writeString(t.majorBrand),s.writeUint(t.minorVersion,4);for(const r of t.compatibleBrands)s.writeString(r);return s}function El(t){const e=t.name.length+1,s=new F("hdlr",32+e);return s.writeFullBox(t.version,t.flags),s.writeUint(t.preDefined,4),s.writeString(t.handlerType),s.writeArray(t.reserved,qe,4,3),s.writeTerminatedString(t.name),s}function Il(t){const e=new F("mdat",8+t.data.length);return e.writeBytes(t.data),e}function Sl(t){const e=t.version===1?8:4,s=8,r=4,i=e*3,n=new F("mdhd",s+r+i+4+2+2);n.writeFullBox(t.version,t.flags),n.writeUint(t.creationTime,e),n.writeUint(t.modificationTime,e),n.writeUint(t.timescale,4),n.writeUint(t.duration,e);const a=t.language.length>=3?(t.language.charCodeAt(0)-96&31)<<10|(t.language.charCodeAt(1)-96&31)<<5|t.language.charCodeAt(2)-96&31:0;return n.writeUint(a,2),n.writeUint(t.preDefined,2),n}function Al(t){const e=new F("mfhd",16);return e.writeFullBox(t.version,t.flags),e.writeUint(t.sequenceNumber,4),e}function xl(t){const e=t.version===1?8:4,s=8,r=4,i=e*3,n=new F("mvhd",s+r+i+4+4+2+2+8+36+24+4);return n.writeFullBox(t.version,t.flags),n.writeUint(t.creationTime,e),n.writeUint(t.modificationTime,e),n.writeUint(t.timescale,4),n.writeUint(t.duration,e),n.writeTemplate(t.rate,4),n.writeTemplate(t.volume,2),n.writeUint(t.reserved1,2),n.writeArray(t.reserved2,qe,4,2),n.writeArray(t.matrix,Ut,4,9),n.writeArray(t.preDefined,qe,4,6),n.writeUint(t.nextTrackId,4),n}function Tl(t){const e=new F("smhd",16);return e.writeFullBox(t.version,t.flags),e.writeUint(t.balance,2),e.writeUint(t.reserved,2),e}function Rl(t,e){const s=t.entries.length,{bytes:r,size:i}=Cs(t.entries,e),n=new F("stsd",16+i);return n.writeFullBox(t.version,t.flags),n.writeUint(s,4),n.writeBytes(r),n}function Pl(t){const e=t.entryCount*8,s=new F("stts",16+e);s.writeFullBox(t.version,t.flags),s.writeUint(t.entryCount,4);for(const r of t.entries)s.writeUint(r.sampleCount,4),s.writeUint(r.sampleDelta,4);return s}function Ol(t){const e=t.version===1?8:4,s=8,r=4,i=e,n=new F("tfdt",s+r+i);return n.writeFullBox(t.version,t.flags),n.writeUint(t.baseMediaDecodeTime,e),n}function Nl(t){const e=t.flags&1?8:0,s=t.flags&2?4:0,r=t.flags&8?4:0,i=t.flags&16?4:0,n=t.flags&32?4:0,a=new F("tfhd",16+e+s+r+i+n);return a.writeFullBox(t.version,t.flags),a.writeUint(t.trackId,4),t.flags&1&&a.writeUint(t.baseDataOffset??0,8),t.flags&2&&a.writeUint(t.sampleDescriptionIndex??0,4),t.flags&8&&a.writeUint(t.defaultSampleDuration??0,4),t.flags&16&&a.writeUint(t.defaultSampleSize??0,4),t.flags&32&&a.writeUint(t.defaultSampleFlags??0,4),a}function Ul(t){const e=t.version===1?8:4,s=8,r=4,i=e*3,n=new F("tkhd",s+r+i+4+4+8+2+2+2+2+36+4+4);return n.writeFullBox(t.version,t.flags),n.writeUint(t.creationTime,e),n.writeUint(t.modificationTime,e),n.writeUint(t.trackId,4),n.writeUint(t.reserved1,4),n.writeUint(t.duration,e),n.writeArray(t.reserved2,qe,4,2),n.writeUint(t.layer,2),n.writeUint(t.alternateGroup,2),n.writeTemplate(t.volume,2),n.writeUint(t.reserved3,2),n.writeArray(t.matrix,Ut,4,9),n.writeTemplate(t.width,4),n.writeTemplate(t.height,4),n}function zl(t){const e=new F("trex",32);return e.writeFullBox(t.version,t.flags),e.writeUint(t.trackId,4),e.writeUint(t.defaultSampleDescriptionIndex,4),e.writeUint(t.defaultSampleDuration,4),e.writeUint(t.defaultSampleSize,4),e.writeUint(t.defaultSampleFlags,4),e}function ql(t){const e=t.flags&1?4:0,s=t.flags&4?4:0;let r=0;t.flags&256&&(r+=4),t.flags&512&&(r+=4),t.flags&1024&&(r+=4),t.flags&2048&&(r+=4);const i=r*t.sampleCount,n=new F("trun",16+e+s+i);n.writeFullBox(t.version,t.flags),n.writeUint(t.sampleCount,4),t.flags&1&&n.writeUint(t.dataOffset??0,4),t.flags&4&&n.writeUint(t.firstSampleFlags??0,4);for(const a of t.samples)t.flags&256&&n.writeUint(a.sampleDuration??0,4),t.flags&512&&n.writeUint(a.sampleSize??0,4),t.flags&1024&&n.writeUint(a.sampleFlags??0,4),t.flags&2048&&n.writeUint(a.sampleCompositionTimeOffset??0,4);return n}function $l(t){const e=t.location.length+1,s=new F("url ",12+e);return s.writeFullBox(t.version,t.flags),s.writeTerminatedString(t.location),s}function Cl(t){const e=new F("vmhd",20);return e.writeFullBox(t.version,t.flags),e.writeUint(t.graphicsmode,2),e.writeArray(t.opcolor,qe,2,3),e}const Ki={mfhd:bl,tfhd:yl,tfdt:gl,trun:vl,mdat:ml};function tt(t,e){for(const s of t){if(e(s))return s;const r=s.boxes;if(r&&Array.isArray(r)){const i=tt(r,e);if(i)return i}}}function Yi(t){const e=new ArrayBuffer(t.byteLength);return new Uint8Array(e).set(t),e}function vt(t){return e=>e.type===t}function Qi(t,e){const s=Xi(Yi(t),{readers:Ki});return(tt(s,vt("tfdt"))?.baseMediaDecodeTime??0)*1e6/e}function en(t,e){const s=Xi(Yi(t),{readers:Ki}),r=tt(s,vt("tfdt"))?.baseMediaDecodeTime??0,i=tt(s,vt("tfhd")),n=i?.defaultSampleDuration??0,a=i?.defaultSampleSize??0,o=i?.defaultSampleFlags??0,c=tt(s,vt("trun"));if(!c)throw new Error("No trun box found in data segment");const u=tt(s,vt("mdat"));if(!u)throw new Error("No mdat box found in data segment");const d=u.data;if(!d)throw new Error("No data in mdat box");const h=[];let p=0,w=r;for(let y=0;y<c.sampleCount;y++){const E=c.samples[y]??{},D=E.sampleSize??a,J=E.sampleDuration??n;if(D<=0)throw new Error(`Invalid sample size ${D} for sample ${y} in trun`);if(J<=0)throw new Error(`Invalid sample duration ${J} for sample ${y} in trun`);if(p+D>d.length)throw new Error(`Sample ${y} would overflow mdat: offset=${p}, size=${D}, mdatLength=${d.length}`);const q=y===0&&c.firstSampleFlags!==void 0?c.firstSampleFlags:E.sampleFlags??o,A=E.sampleCompositionTimeOffset??0,U=new Uint8Array(d.slice(p,p+D));p+=D;const Z=w+A,T=Math.round(Z*1e6/e),re=q===0||(q&65536)===0;h.push({data:U,timestamp:T,keyframe:re}),w+=J}return h}function ke(t){if(t=t.startsWith("0x")?t.slice(2):t,t.length%2)throw new Error("invalid hex string length");const e=t.match(/.{2}/g);if(!e)throw new Error("invalid hex string format");return new Uint8Array(e.map(s=>parseInt(s,16)))}const Ht=[65536,0,0,0,65536,0,0,0,1073741824],Dl={ftyp:kl,mvhd:xl,tkhd:Ul,mdhd:Sl,hdlr:El,vmhd:Cl,smhd:Tl,"url ":$l,dref:_l,stsd:Rl,stts:Pl,trex:zl,mfhd:Al,tfhd:Nl,tfdt:Ol,trun:ql,mdat:Il};function It(t){return wl(t,{writers:Dl})}function Ds(t,e,s,r){const i=12+r.length,n=new Uint8Array(i),a=new DataView(n.buffer);return a.setUint32(0,i,!1),n[4]=t.charCodeAt(0),n[5]=t.charCodeAt(1),n[6]=t.charCodeAt(2),n[7]=t.charCodeAt(3),a.setUint32(8,e<<24|s,!1),n.set(r,12),n}function tn(){const t=new Uint8Array(4);return Ds("stsc",0,0,t)}function sn(){const t=new Uint8Array(8);return Ds("stsz",0,0,t)}function rn(){const t=new Uint8Array(4);return Ds("stco",0,0,t)}function Fl(t,e,s){const r=8+s.length,i=8+(78+r),n=new Uint8Array(i),a=new DataView(n.buffer);let o=0;return a.setUint32(o,i,!1),o+=4,n[o++]=97,n[o++]=118,n[o++]=99,n[o++]=49,o+=6,a.setUint16(o,1,!1),o+=2,a.setUint16(o,0,!1),o+=2,a.setUint16(o,0,!1),o+=2,o+=12,a.setUint16(o,t,!1),o+=2,a.setUint16(o,e,!1),o+=2,a.setUint32(o,4718592,!1),o+=4,a.setUint32(o,4718592,!1),o+=4,a.setUint32(o,0,!1),o+=4,a.setUint16(o,1,!1),o+=2,o+=32,a.setUint16(o,24,!1),o+=2,a.setUint16(o,65535,!1),o+=2,a.setUint32(o,r,!1),o+=4,n[o++]=97,n[o++]=118,n[o++]=99,n[o++]=67,n.set(s,o),n}function Dr(t){const{codedWidth:e,codedHeight:s,description:r,container:i}=t;if(!e||!s||!r)throw new Error("Missing required fields to create video init segment");const n=i.kind==="cmaf"?i.timescale:1e6,a=i.kind==="cmaf"?i.trackId:1,o={type:"ftyp",majorBrand:"isom",minorVersion:512,compatibleBrands:["isom","iso6","mp41"]},c={type:"mvhd",version:0,flags:0,creationTime:0,modificationTime:0,timescale:n,duration:0,rate:65536,volume:256,reserved1:0,reserved2:[0,0],matrix:Ht,preDefined:[0,0,0,0,0,0],nextTrackId:a+1},u={type:"tkhd",version:0,flags:3,creationTime:0,modificationTime:0,trackId:a,reserved1:0,duration:0,reserved2:[0,0],layer:0,alternateGroup:0,volume:0,reserved3:0,matrix:Ht,width:e*65536,height:s*65536},d={type:"mdhd",version:0,flags:0,creationTime:0,modificationTime:0,timescale:n,duration:0,language:"und",preDefined:0},h={type:"hdlr",version:0,flags:0,preDefined:0,handlerType:"vide",reserved:[0,0,0],name:"VideoHandler"},p={type:"vmhd",version:0,flags:1,graphicsmode:0,opcolor:[0,0,0]},w={type:"dinf",boxes:[{type:"dref",version:0,flags:0,entryCount:1,entries:[{type:"url ",version:0,flags:1,location:""}]}]},y={type:"stsd",version:0,flags:0,entryCount:1,entries:[Fl(e,s,ke(r))]},E={type:"stts",version:0,flags:0,entryCount:0,entries:[]},D=tn(),J=sn(),q=rn(),A=It([o,{type:"moov",boxes:[c,{type:"trak",boxes:[u,{type:"mdia",boxes:[d,h,{type:"minf",boxes:[p,w,{type:"stbl",boxes:[y,E,D,J,q]}]}]}]},{type:"mvex",boxes:[{type:"trex",version:0,flags:0,trackId:a,defaultSampleDescriptionIndex:1,defaultSampleDuration:0,defaultSampleSize:0,defaultSampleFlags:0}]}]}]),U=A.reduce((re,Ee)=>re+Ee.byteLength,0),Z=new Uint8Array(U);let T=0;for(const re of A)Z.set(new Uint8Array(re.buffer,re.byteOffset,re.byteLength),T),T+=re.byteLength;return Z}function Fr(t){const{sampleRate:e,numberOfChannels:s,description:r,codec:i,container:n}=t,a=n.kind==="cmaf"?n.timescale:1e6,o=n.kind==="cmaf"?n.trackId:1,c={type:"ftyp",majorBrand:"isom",minorVersion:512,compatibleBrands:["isom","iso6","mp41"]},u={type:"mvhd",version:0,flags:0,creationTime:0,modificationTime:0,timescale:a,duration:0,rate:65536,volume:256,reserved1:0,reserved2:[0,0],matrix:Ht,preDefined:[0,0,0,0,0,0],nextTrackId:o+1},d={type:"tkhd",version:0,flags:3,creationTime:0,modificationTime:0,trackId:o,reserved1:0,duration:0,reserved2:[0,0],layer:0,alternateGroup:0,volume:256,reserved3:0,matrix:Ht,width:0,height:0},h={type:"mdhd",version:0,flags:0,creationTime:0,modificationTime:0,timescale:a,duration:0,language:"und",preDefined:0},p={type:"hdlr",version:0,flags:0,preDefined:0,handlerType:"soun",reserved:[0,0,0],name:"SoundHandler"},w={type:"smhd",version:0,flags:0,balance:0,reserved:0},y={type:"dinf",boxes:[{type:"dref",version:0,flags:0,entryCount:1,entries:[{type:"url ",version:0,flags:1,location:""}]}]},E={type:"stsd",version:0,flags:0,entryCount:1,entries:[Ml(i,e,s,r)]},D={type:"stts",version:0,flags:0,entryCount:0,entries:[]},J=tn(),q=sn(),A=rn(),U=It([c,{type:"moov",boxes:[u,{type:"trak",boxes:[d,{type:"mdia",boxes:[h,p,{type:"minf",boxes:[w,y,{type:"stbl",boxes:[E,D,J,q,A]}]}]}]},{type:"mvex",boxes:[{type:"trex",version:0,flags:0,trackId:o,defaultSampleDescriptionIndex:1,defaultSampleDuration:0,defaultSampleSize:0,defaultSampleFlags:0}]}]}]),Z=U.reduce((Ee,ln)=>Ee+ln.byteLength,0),T=new Uint8Array(Z);let re=0;for(const Ee of U)T.set(new Uint8Array(Ee.buffer,Ee.byteOffset,Ee.byteLength),re),re+=Ee.byteLength;return T}function Ml(t,e,s,r){if(t.startsWith("mp4a"))return Ll(e,s,r);if(t==="opus")return Bl(e,s,r);throw new Error(`Unsupported audio codec: ${t}`)}function Ll(t,e,s){const r=jl(t,e,s),i=8+(28+r.length),n=new Uint8Array(i),a=new DataView(n.buffer);let o=0;return a.setUint32(o,i,!1),o+=4,n[o++]=109,n[o++]=112,n[o++]=52,n[o++]=97,o+=6,a.setUint16(o,1,!1),o+=2,o+=8,a.setUint16(o,e,!1),o+=2,a.setUint16(o,16,!1),o+=2,a.setUint16(o,0,!1),o+=2,a.setUint16(o,0,!1),o+=2,a.setUint32(o,t*65536,!1),o+=4,n.set(r,o),n}function Bl(t,e,s){const r=Gl(e,t,s),i=8+(28+r.length),n=new Uint8Array(i),a=new DataView(n.buffer);let o=0;return a.setUint32(o,i,!1),o+=4,n[o++]=79,n[o++]=112,n[o++]=117,n[o++]=115,o+=6,a.setUint16(o,1,!1),o+=2,o+=8,a.setUint16(o,e,!1),o+=2,a.setUint16(o,16,!1),o+=2,a.setUint16(o,0,!1),o+=2,a.setUint16(o,0,!1),o+=2,a.setUint32(o,t*65536,!1),o+=4,n.set(r,o),n}function Vl(t,e){const s={96e3:0,88200:1,64e3:2,48e3:3,44100:4,32e3:5,24e3:6,22050:7,16e3:8,12e3:9,11025:10,8e3:11,7350:12}[t]??4,r=16|s>>1,i=(s&1)<<7|e<<3;return new Uint8Array([r,i])}function jl(t,e,s){const r=s?ke(s):Vl(t,e),i=r.length,n=15+i,a=5+n+3,o=14+a,c=new Uint8Array(o),u=new DataView(c.buffer);let d=0;return u.setUint32(d,o,!1),d+=4,c[d++]=101,c[d++]=115,c[d++]=100,c[d++]=115,u.setUint32(d,0,!1),d+=4,c[d++]=3,c[d++]=a,u.setUint16(d,0,!1),d+=2,c[d++]=0,c[d++]=4,c[d++]=n,c[d++]=64,c[d++]=21,c[d++]=0,c[d++]=0,c[d++]=0,u.setUint32(d,0,!1),d+=4,u.setUint32(d,0,!1),d+=4,c[d++]=5,c[d++]=i,c.set(r,d),d+=i,c[d++]=6,c[d++]=1,c[d++]=2,c}function Gl(t,e,s){if(s){const o=ke(s),c=8+o.length,u=new Uint8Array(c);return new DataView(u.buffer).setUint32(0,c,!1),u[4]=100,u[5]=79,u[6]=112,u[7]=115,u.set(o,8),u}const r=19,i=new Uint8Array(r),n=new DataView(i.buffer);let a=0;return n.setUint32(a,r,!1),a+=4,i[a++]=100,i[a++]=79,i[a++]=112,i[a++]=115,i[a++]=0,i[a++]=t,n.setUint16(a,312,!1),a+=2,n.setUint32(a,e,!1),a+=4,n.setInt16(a,0,!1),a+=2,i[a++]=0,i}function nn(t){const{data:e,timestamp:s,duration:r,keyframe:i,sequence:n,trackId:a=1}=t,o=i?33554432:16842752,c={type:"mfhd",version:0,flags:0,sequenceNumber:n},u={type:"tfhd",version:0,flags:131072,trackId:a},d={type:"tfdt",version:1,flags:0,baseMediaDecodeTime:s},h={type:"trun",version:0,flags:1793,sampleCount:1,dataOffset:0,samples:[{sampleDuration:r,sampleSize:e.byteLength,sampleFlags:o}]},p={type:"moof",boxes:[c,{type:"traf",boxes:[u,d,h]}]},w=It([p]);let y=0;for(const T of w)y+=T.byteLength;h.dataOffset=y+8;const E=It([p]);y=0;for(const T of E)y+=T.byteLength;const D=new ArrayBuffer(e.byteLength),J=new Uint8Array(D);J.set(e);const q=It([{type:"mdat",data:J}]);let A=0;for(const T of q)A+=T.byteLength;const U=new Uint8Array(y+A);let Z=0;for(const T of E)U.set(new Uint8Array(T.buffer,T.byteOffset,T.byteLength),Z),Z+=T.byteLength;for(const T of q)U.set(new Uint8Array(T.buffer,T.byteOffset,T.byteLength),Z),Z+=T.byteLength;return U}class bt{#e;#t;#s=[];#r;#i;#n=new f([]);buffered=this.#n;#a=new C;constructor(e,s){this.#e=e,this.#t=f.from(s?.latency??g.zero),this.#a.spawn(this.#o.bind(this)),this.#a.cleanup(()=>{this.#e.close();for(const r of this.#s)r.consumer.close();this.#s.length=0})}async#o(){for(;;){const e=await this.#e.nextGroup();if(!e)break;if(this.#r===void 0&&(this.#r=e.sequence),e.sequence<this.#r){console.warn(`skipping old group: ${e.sequence} < ${this.#r}`),e.close();continue}const s={consumer:e,frames:[]};this.#s.push(s),this.#s.sort((r,i)=>r.consumer.sequence-i.consumer.sequence),this.#a.spawn(this.#c.bind(this,s))}}async#c(e){try{let s=!0;for(;;){const r=await e.consumer.readFrame();if(!r)break;const{data:i,timestamp:n}=bt.#l(r),a={data:i,timestamp:n,keyframe:s};s=!1,e.frames.push(a),(e.latest===void 0||n>e.latest)&&(e.latest=n),this.#u(),e.consumer.sequence===this.#r?(this.#i?.(),this.#i=void 0):this.#d()}}catch{}finally{e.done=!0,e.consumer.sequence===this.#r&&(this.#r+=1),this.#u(),this.#i?.(),this.#i=void 0,e.consumer.close()}}#d(){if(this.#r===void 0)return;let e=!1;for(;this.#s.length>=2;){const s=Rs.fromMilli(this.#t.peek());let r,i;for(const a of this.#s){if(a.latest===void 0)continue;const o=a.frames.at(0)?.timestamp??a.latest;(r===void 0||o<r)&&(r=o),(i===void 0||a.latest>i)&&(i=a.latest)}if(r===void 0||i===void 0||i-r<=s)break;const n=this.#s.shift();if(!n)break;this.#r=this.#s[0]?.consumer.sequence,console.warn(`skipping slow group: ${n.consumer.sequence} -> ${this.#r}`),n.consumer.close(),n.frames.length=0,e=!0}e&&(this.#u(),this.#i?.(),this.#i=void 0)}async next(){for(;;){if(this.#s.length>0&&this.#r!==void 0&&this.#s[0].consumer.sequence<=this.#r){const s=this.#s[0].frames.shift();if(s)return this.#u(),{frame:s,group:this.#s[0].consumer.sequence};if(this.#r>this.#s[0].consumer.sequence||this.#s[0].done){this.#s[0].consumer.sequence===this.#r&&(this.#r+=1);const r=this.#s.shift();if(r)return this.#u(),{frame:void 0,group:r.consumer.sequence}}}if(this.#i)throw new Error("multiple calls to decode not supported");const e=new Promise(s=>{this.#i=s}).then(()=>!0);if(!await Promise.race([e,this.#a.closed])){this.#i=void 0;return}}}static#l(e){const[s,r]=ms(e);return{timestamp:s,data:r}}#u(){const e=[];let s;for(const r of this.#s){const i=r.frames.at(0);if(!i||r.latest===void 0)continue;const n=g.fromMicro(i.timestamp),a=g.fromMicro(r.latest),o=e.at(-1),c=s?.done&&s.consumer.sequence+1===r.consumer.sequence;o&&(o.end>=n||c)?o.end=g.max(o.end,a):e.push({start:n,end:a}),s=r}this.#n.set(e)}close(){this.#a.close();for(const e of this.#s)e.consumer.close(),e.frames.length=0;this.#s.length=0}}navigator.userAgent.toLowerCase().includes("chrome");navigator.userAgent.toLowerCase().includes("firefox");let ds;async function Mr(){return globalThis.AudioEncoder&&globalThis.AudioDecoder?!0:(ds||(console.warn("using Opus polyfill; performance may be degraded"),ds=Promise.all([ar(()=>import("./libav-opus-af-BlMWboA7-CFTeN5TA.js"),[],import.meta.url),ar(()=>import("./main-DGBFe0O7-CIZu5tmC.js"),[],import.meta.url)]).then(async([t,e])=>(await e.load({LibAV:t,polyfill:!0}),!0))),await ds)}const Zl=`var __defProp = Object.defineProperty;
var __export = (target, all) => {
  for (var name in all)
    __defProp(target, name, { get: all[name], enumerable: true });
};

// ../../node_modules/.bun/dequal@2.0.3/node_modules/dequal/dist/index.mjs
var has = Object.prototype.hasOwnProperty;
function find(iter, tar, key) {
  for (key of iter.keys()) {
    if (dequal(key, tar)) return key;
  }
}
function dequal(foo, bar) {
  var ctor, len, tmp;
  if (foo === bar) return true;
  if (foo && bar && (ctor = foo.constructor) === bar.constructor) {
    if (ctor === Date) return foo.getTime() === bar.getTime();
    if (ctor === RegExp) return foo.toString() === bar.toString();
    if (ctor === Array) {
      if ((len = foo.length) === bar.length) {
        while (len-- && dequal(foo[len], bar[len])) ;
      }
      return len === -1;
    }
    if (ctor === Set) {
      if (foo.size !== bar.size) {
        return false;
      }
      for (len of foo) {
        tmp = len;
        if (tmp && typeof tmp === "object") {
          tmp = find(bar, tmp);
          if (!tmp) return false;
        }
        if (!bar.has(tmp)) return false;
      }
      return true;
    }
    if (ctor === Map) {
      if (foo.size !== bar.size) {
        return false;
      }
      for (len of foo) {
        tmp = len[0];
        if (tmp && typeof tmp === "object") {
          tmp = find(bar, tmp);
          if (!tmp) return false;
        }
        if (!dequal(len[1], bar.get(tmp))) {
          return false;
        }
      }
      return true;
    }
    if (ctor === ArrayBuffer) {
      foo = new Uint8Array(foo);
      bar = new Uint8Array(bar);
    } else if (ctor === DataView) {
      if ((len = foo.byteLength) === bar.byteLength) {
        while (len-- && foo.getInt8(len) === bar.getInt8(len)) ;
      }
      return len === -1;
    }
    if (ArrayBuffer.isView(foo)) {
      if ((len = foo.byteLength) === bar.byteLength) {
        while (len-- && foo[len] === bar[len]) ;
      }
      return len === -1;
    }
    if (!ctor || typeof foo === "object") {
      len = 0;
      for (ctor in foo) {
        if (has.call(foo, ctor) && ++len && !has.call(bar, ctor)) return false;
        if (!(ctor in bar) || !dequal(foo[ctor], bar[ctor])) return false;
      }
      return Object.keys(bar).length === len;
    }
  }
  return foo !== foo && bar !== bar;
}

// ../signals/src/index.ts
var DEV = typeof import.meta.env !== "undefined" && import.meta.env?.MODE !== "production";
var SIGNAL_BRAND = /* @__PURE__ */ Symbol.for("@moq/signals");
var Signal = class _Signal {
  #value;
  #subscribers = /* @__PURE__ */ new Set();
  #changed = /* @__PURE__ */ new Set();
  // Brand to identify this as a Signal across package instances
  [SIGNAL_BRAND] = true;
  constructor(value) {
    this.#value = value;
  }
  static from(value) {
    if (typeof value === "object" && value !== null && SIGNAL_BRAND in value) {
      return value;
    }
    return new _Signal(value);
  }
  get() {
    return this.#value;
  }
  // TODO rename to \`get\` once we've ported everything
  peek() {
    return this.#value;
  }
  // Set the current value, by default notifying subscribers if the value is different.
  // If notify is undefined, we'll check if the value has changed after the microtask.
  set(value, notify) {
    const old = this.#value;
    this.#value = value;
    if (notify === false) return;
    if (notify === void 0 && old === this.#value) {
      if (DEV && value !== null && (typeof value === "object" || typeof value === "function")) {
        console.warn(
          "Signal.set() called with the same object reference. Changes won't propagate. Use update() or mutate() instead."
        );
      }
      return;
    }
    if (this.#subscribers.size === 0 && this.#changed.size === 0) return;
    const subscribers = this.#subscribers;
    const changed = this.#changed;
    this.#changed = /* @__PURE__ */ new Set();
    queueMicrotask(() => {
      if (notify === void 0 && dequal(old, this.#value)) {
        for (const fn of changed) {
          this.#changed.add(fn);
        }
        return;
      }
      for (const fn of subscribers) {
        try {
          fn(value);
        } catch (error2) {
          console.error("signal subscriber error", error2);
        }
      }
      for (const fn of changed) {
        try {
          fn(value);
        } catch (error2) {
          console.error("signal changed error", error2);
        }
      }
    });
  }
  // Mutate the current value and notify subscribers unless notify is false.
  // Unlike set, we can't use a dequal check because the function may mutate the value.
  update(fn, notify = true) {
    const value = fn(this.#value);
    this.set(value, notify);
  }
  // Mutate the current value and notify subscribers unless notify is false.
  mutate(fn, notify = true) {
    const r = fn(this.#value);
    this.set(this.#value, notify);
    return r;
  }
  // Receive a notification each time the value changes.
  subscribe(fn) {
    this.#subscribers.add(fn);
    if (DEV && this.#subscribers.size >= 100 && Number.isInteger(Math.log10(this.#subscribers.size))) {
      throw new Error("signal has too many subscribers; may be leaking");
    }
    return () => this.#subscribers.delete(fn);
  }
  // Receive a notification when the value changes.
  changed(fn) {
    this.#changed.add(fn);
    return () => this.#changed.delete(fn);
  }
  // Receive a notification when the value changes AND with the initial value.
  watch(fn) {
    const dispose = this.subscribe(fn);
    queueMicrotask(() => fn(this.#value));
    return dispose;
  }
  static async race(...sigs) {
    const dispose = [];
    const result = await new Promise((resolve) => {
      for (const sig of sigs) {
        dispose.push(sig.changed(resolve));
      }
    });
    for (const fn of dispose) fn();
    return result;
  }
};
var Effect = class _Effect {
  // Sanity check to make sure roots are being disposed on dev.
  static #finalizer = new FinalizationRegistry((debugInfo) => {
    console.warn(\`Signals was garbage collected without being closed:
\${debugInfo}\`);
  });
  #fn;
  #dispose = [];
  #unwatch = [];
  #async = [];
  #stack;
  #scheduled = false;
  #stop;
  #stopped;
  #close;
  #closed;
  // If a function is provided, it will be run with the effect as an argument.
  constructor(fn) {
    if (DEV) {
      const debug = new Error("created here:").stack ?? "No stack";
      _Effect.#finalizer.register(this, debug, this);
    }
    this.#fn = fn;
    if (DEV) {
      this.#stack = new Error().stack;
    }
    this.#stopped = new Promise((resolve) => {
      this.#stop = resolve;
    });
    this.#closed = new Promise((resolve) => {
      this.#close = resolve;
    });
    if (fn) {
      this.#schedule();
    }
  }
  #schedule() {
    if (this.#scheduled) return;
    this.#scheduled = true;
    queueMicrotask(
      () => this.#run().catch((error2) => {
        console.error("effect error", error2, this.#stack);
      })
    );
  }
  async #run() {
    if (this.#dispose === void 0) return;
    this.#stop();
    this.#stopped = new Promise((resolve) => {
      this.#stop = resolve;
    });
    for (const unwatch of this.#unwatch) unwatch();
    this.#unwatch.length = 0;
    for (const fn of this.#dispose) fn();
    this.#dispose.length = 0;
    if (this.#async.length > 0) {
      try {
        let warn;
        const timeout = new Promise((resolve) => {
          warn = setTimeout(() => {
            if (DEV) {
              console.warn("spawn is still running after 5s; continuing anyway", this.#stack);
            }
            resolve();
          }, 5e3);
        });
        await Promise.race([Promise.all(this.#async), timeout]);
        if (warn) clearTimeout(warn);
        this.#async.length = 0;
      } catch (error2) {
        console.error("async effect error", error2);
        if (this.#stack) console.error("stack", this.#stack);
      }
    }
    if (this.#dispose === void 0) return;
    this.#scheduled = false;
    if (this.#fn) {
      this.#fn(this);
      if (DEV && this.#dispose !== void 0 && this.#unwatch.length === 0 && this.#dispose.length === 0 && this.#async.length === 0) {
        console.warn("Effect did not subscribe to any signals; it will never rerun.", this.#stack);
      }
    }
  }
  // Get the current value of a signal, monitoring it for changes (via ===) and rerunning on change.
  get(signal) {
    if (this.#dispose === void 0) {
      if (DEV) {
        console.warn("Effect.get called when closed, returning current value");
      }
      return signal.peek();
    }
    const value = signal.peek();
    const dispose = signal.changed(() => this.#schedule());
    this.#unwatch.push(dispose);
    return value;
  }
  // Temporarily set the value of a signal, unsetting it on cleanup.
  // The last argument is the cleanup value, set before the effect is rerun.
  // It's optional only if T can be undefined.
  set(signal, value, ...args) {
    if (this.#dispose === void 0) {
      if (DEV) {
        console.warn("Effect.set called when closed, ignoring");
      }
      return;
    }
    signal.set(value);
    const cleanup = args[0];
    const cleanupValue = cleanup === void 0 ? void 0 : cleanup;
    this.cleanup(() => signal.set(cleanupValue));
  }
  // Spawn an async effect that blocks the effect being reloaded until it completes.
  // Use this.cancel if you need to detect when the effect is reloading to terminate.
  // TODO: Add effect for another layer of nesting
  spawn(fn) {
    const promise = fn().catch((error2) => {
      console.error("spawn error", error2);
    });
    if (this.#dispose === void 0) {
      if (DEV) {
        console.warn("Effect.spawn called when closed");
      }
      return;
    }
    this.#async.push(promise);
  }
  // Run the function after the given delay in milliseconds UNLESS the effect is cleaned up first.
  timer(fn, ms) {
    if (this.#dispose === void 0) {
      if (DEV) {
        console.warn("Effect.timer called when closed, ignoring");
      }
      return;
    }
    let timeout;
    timeout = setTimeout(() => {
      timeout = void 0;
      fn();
    }, ms);
    this.cleanup(() => timeout && clearTimeout(timeout));
  }
  // Run the function, and clean up the nested effect after the given delay.
  timeout(fn, ms) {
    if (this.#dispose === void 0) {
      if (DEV) {
        console.warn("Effect.timeout called when closed, ignoring");
      }
      return;
    }
    const effect = new _Effect(fn);
    let timeout = setTimeout(() => {
      effect.close();
      timeout = void 0;
    }, ms);
    this.#dispose.push(() => {
      if (timeout) {
        clearTimeout(timeout);
        effect.close();
      }
    });
  }
  // Run the callback on the next animation frame, unless the effect is cleaned up first.
  animate(fn) {
    if (this.#dispose === void 0) {
      if (DEV) {
        console.warn("Effect.animate called when closed, ignoring");
      }
      return;
    }
    let animate = requestAnimationFrame((now) => {
      fn(now);
      animate = void 0;
    });
    this.cleanup(() => {
      if (animate) cancelAnimationFrame(animate);
    });
  }
  interval(fn, ms) {
    if (this.#dispose === void 0) {
      if (DEV) {
        console.warn("Effect.interval called when closed, ignoring");
      }
      return;
    }
    const interval = setInterval(() => {
      fn();
    }, ms);
    this.cleanup(() => clearInterval(interval));
  }
  // Create a nested effect that can be rerun independently.
  run(fn) {
    if (this.#dispose === void 0) {
      if (DEV) {
        console.warn("Effect.nested called when closed, ignoring");
      }
      return;
    }
    const effect = new _Effect(fn);
    this.#dispose.push(() => effect.close());
  }
  // Backwards compatibility with the old name.
  effect(fn) {
    return this.run(fn);
  }
  // Get the values of multiple signals, returning undefined if any are falsy.
  getAll(signals) {
    const values = [];
    for (const signal of signals) {
      const value = this.get(signal);
      if (!value) return void 0;
      values.push(value);
    }
    return values;
  }
  // A helper to call a function when a signal changes.
  subscribe(signal, fn) {
    if (this.#dispose === void 0) {
      if (DEV) {
        console.warn("Effect.subscribe called when closed, running once");
      }
      fn(signal.peek());
      return;
    }
    this.run((effect) => {
      const value = effect.get(signal);
      fn(value);
    });
  }
  event(target, type, listener, options) {
    if (this.#dispose === void 0) {
      if (DEV) {
        console.warn("Effect.eventListener called when closed, ignoring");
      }
      return;
    }
    target.addEventListener(type, listener, options);
    this.cleanup(() => target.removeEventListener(type, listener, options));
  }
  // Register a cleanup function.
  cleanup(fn) {
    if (this.#dispose === void 0) {
      if (DEV) {
        console.warn("Effect.cleanup called when closed, running immediately");
      }
      fn();
      return;
    }
    this.#dispose.push(fn);
  }
  close() {
    if (this.#dispose === void 0) {
      return;
    }
    this.#close();
    this.#stop();
    for (const fn of this.#dispose) fn();
    this.#dispose = void 0;
    for (const signal of this.#unwatch) signal();
    this.#unwatch.length = 0;
    this.#async.length = 0;
    if (DEV) {
      _Effect.#finalizer.unregister(this);
    }
  }
  get closed() {
    return this.#closed;
  }
  get cancel() {
    return this.#stopped;
  }
  proxy(dst, src) {
    this.subscribe(src, (value) => dst.update(() => value));
  }
};

// ../lite/src/path.ts
function from(...paths) {
  const joined = paths.join("/");
  return joined.replace(/\\/+/g, "/").replace(/^\\/+/, "").replace(/\\/+$/, "");
}

// ../../node_modules/.bun/@moq+web-transport-ws@0.1.2/node_modules/@moq/web-transport-ws/varint.js
var VarInt = class _VarInt {
  static MAX = (1n << 62n) - 1n;
  static MAX_SIZE = 8;
  value;
  constructor(value) {
    if (value < 0n || value > _VarInt.MAX) {
      throw new Error(\`VarInt value out of range: \${value}\`);
    }
    this.value = value;
  }
  static from(value) {
    return new _VarInt(BigInt(value));
  }
  size() {
    const x = this.value;
    if (x < 2n ** 6n)
      return 1;
    if (x < 2n ** 14n)
      return 2;
    if (x < 2n ** 30n)
      return 4;
    if (x < 2n ** 62n)
      return 8;
    throw new Error("VarInt value too large");
  }
  // Append to the provided buffer
  encode(dst) {
    const x = this.value;
    const size = this.size();
    if (dst.byteOffset + dst.byteLength + size > dst.buffer.byteLength) {
      throw new Error("destination buffer too small");
    }
    const view = new DataView(dst.buffer, dst.byteOffset + dst.byteLength, size);
    if (size === 1) {
      view.setUint8(0, Number(x));
    } else if (size === 2) {
      view.setUint16(0, 1 << 14 | Number(x), false);
    } else if (size === 4) {
      view.setUint32(0, 2 << 30 | Number(x), false);
    } else if (size === 8) {
      view.setBigUint64(0, 3n << 62n | x, false);
    } else {
      throw new Error("VarInt value too large");
    }
    return new Uint8Array(dst.buffer, dst.byteOffset, dst.byteLength + size);
  }
  static decode(buffer) {
    if (buffer.byteLength < 1) {
      throw new Error("Unexpected end of buffer");
    }
    const view = new DataView(buffer.buffer, buffer.byteOffset);
    const firstByte = view.getUint8(0);
    const tag = firstByte >> 6;
    let value;
    let bytesRead;
    switch (tag) {
      case 0:
        value = BigInt(firstByte & 63);
        bytesRead = 1;
        break;
      case 1:
        if (2 > buffer.length) {
          throw new Error("Unexpected end of buffer");
        }
        value = BigInt(view.getUint16(0, false) & 16383);
        bytesRead = 2;
        break;
      case 2:
        if (4 > buffer.length) {
          throw new Error("Unexpected end of buffer");
        }
        value = BigInt(view.getUint32(0, false) & 1073741823);
        bytesRead = 4;
        break;
      case 3:
        if (8 > buffer.length) {
          throw new Error("Unexpected end of buffer");
        }
        value = view.getBigUint64(0, false) & 0x3fffffffffffffffn;
        bytesRead = 8;
        break;
      default:
        throw new Error("Invalid VarInt tag");
    }
    const remaining = new Uint8Array(buffer.buffer, buffer.byteOffset + bytesRead, buffer.byteLength - bytesRead);
    return [new _VarInt(value), remaining];
  }
};

// ../lite/src/varint.ts
var MAX_U6 = 2 ** 6 - 1;
var MAX_U14 = 2 ** 14 - 1;
var MAX_U30 = 2 ** 30 - 1;
var MAX_U53 = Number.MAX_SAFE_INTEGER;
function setUint8(dst, v) {
  const buffer = new Uint8Array(dst, 0, 1);
  buffer[0] = v;
  return buffer;
}
function setUint16(dst, v) {
  const view = new DataView(dst, 0, 2);
  view.setUint16(0, v);
  return new Uint8Array(view.buffer, view.byteOffset, view.byteLength);
}
function setUint32(dst, v) {
  const view = new DataView(dst, 0, 4);
  view.setUint32(0, v);
  return new Uint8Array(view.buffer, view.byteOffset, view.byteLength);
}
function setUint64(dst, v) {
  const view = new DataView(dst, 0, 8);
  view.setBigUint64(0, v);
  return new Uint8Array(view.buffer, view.byteOffset, view.byteLength);
}
var MAX_U62 = 2n ** 62n - 1n;
function encodeTo(dst, v) {
  const b = BigInt(v);
  if (b < 0n) {
    throw new Error(\`underflow, value is negative: \${v}\`);
  }
  if (b > MAX_U62) {
    throw new Error(\`overflow, value larger than 62-bits: \${v}\`);
  }
  const n = Number(b);
  if (n <= MAX_U6) {
    return setUint8(dst, n);
  }
  if (n <= MAX_U14) {
    return setUint16(dst, n | 16384);
  }
  if (n <= MAX_U30) {
    return setUint32(dst, n | 2147483648);
  }
  return setUint64(dst, b | 0xc000000000000000n);
}
function encode2(v) {
  return encodeTo(new ArrayBuffer(8), v);
}
function decode2(buf) {
  if (buf.length === 0) {
    throw new Error("buffer is empty");
  }
  const size = 1 << ((buf[0] & 192) >> 6);
  if (buf.length < size) {
    throw new Error(\`buffer too short: need \${size} bytes, have \${buf.length}\`);
  }
  const view = new DataView(buf.buffer, buf.byteOffset, size);
  const remain = buf.subarray(size);
  let v;
  if (size === 1) {
    v = buf[0] & 63;
  } else if (size === 2) {
    v = view.getUint16(0) & 16383;
  } else if (size === 4) {
    v = view.getUint32(0) & 1073741823;
  } else if (size === 8) {
    v = Number(view.getBigUint64(0) & 0x3fffffffffffffffn);
  } else {
    throw new Error("impossible");
  }
  return [v, remain];
}

// ../lite/src/stream.ts
var MAX_U31 = 2 ** 31 - 1;
var MAX_READ_SIZE = 1024 * 1024 * 64;
var Reader = class {
  #buffer;
  #stream;
  // if undefined, the buffer is consumed then EOF
  #reader;
  constructor(stream, buffer) {
    this.#buffer = buffer ?? new Uint8Array();
    this.#stream = stream;
    this.#reader = this.#stream?.getReader();
  }
  // Adds more data to the buffer, returning true if more data was added.
  async #fill() {
    if (!this.#reader) {
      return false;
    }
    const result = await this.#reader.read();
    if (result.done) {
      return false;
    }
    if (result.value.byteLength === 0) {
      throw new Error("unexpected empty chunk");
    }
    const buffer = new Uint8Array(result.value);
    if (this.#buffer.byteLength === 0) {
      this.#buffer = buffer;
    } else {
      const temp = new Uint8Array(this.#buffer.byteLength + buffer.byteLength);
      temp.set(this.#buffer);
      temp.set(buffer, this.#buffer.byteLength);
      this.#buffer = temp;
    }
    return true;
  }
  // Add more data to the buffer until it's at least size bytes.
  async #fillTo(size) {
    if (size > MAX_READ_SIZE) {
      throw new Error(\`read size \${size} exceeds max size \${MAX_READ_SIZE}\`);
    }
    while (this.#buffer.byteLength < size) {
      if (!await this.#fill()) {
        throw new Error("unexpected end of stream");
      }
    }
  }
  // Consumes the first size bytes of the buffer.
  #slice(size) {
    const result = new Uint8Array(this.#buffer.buffer, this.#buffer.byteOffset, size);
    this.#buffer = new Uint8Array(
      this.#buffer.buffer,
      this.#buffer.byteOffset + size,
      this.#buffer.byteLength - size
    );
    return result;
  }
  async read(size) {
    if (size === 0) return new Uint8Array();
    await this.#fillTo(size);
    return this.#slice(size);
  }
  async readAll() {
    while (await this.#fill()) {
    }
    return this.#slice(this.#buffer.byteLength);
  }
  async string() {
    const length = await this.u53();
    const buffer = await this.read(length);
    return new TextDecoder().decode(buffer);
  }
  async bool() {
    const v = await this.u8();
    if (v === 0) return false;
    if (v === 1) return true;
    throw new Error("invalid bool value");
  }
  async u8() {
    await this.#fillTo(1);
    return this.#slice(1)[0];
  }
  async u16() {
    await this.#fillTo(2);
    const view = new DataView(this.#buffer.buffer, this.#buffer.byteOffset, 2);
    const result = view.getUint16(0);
    this.#slice(2);
    return result;
  }
  // Returns a Number using 53-bits, the max Javascript can use for integer math
  async u53() {
    const v = await this.u62();
    if (v > MAX_U53) {
      throw new Error("value larger than 53-bits; use v62 instead");
    }
    return Number(v);
  }
  // NOTE: Returns a bigint instead of a number since it may be larger than 53-bits
  async u62() {
    await this.#fillTo(1);
    const size = (this.#buffer[0] & 192) >> 6;
    if (size === 0) {
      const first = this.#slice(1)[0];
      return BigInt(first) & 0x3fn;
    }
    if (size === 1) {
      await this.#fillTo(2);
      const slice2 = this.#slice(2);
      const view2 = new DataView(slice2.buffer, slice2.byteOffset, slice2.byteLength);
      return BigInt(view2.getUint16(0)) & 0x3fffn;
    }
    if (size === 2) {
      await this.#fillTo(4);
      const slice2 = this.#slice(4);
      const view2 = new DataView(slice2.buffer, slice2.byteOffset, slice2.byteLength);
      return BigInt(view2.getUint32(0)) & 0x3fffffffn;
    }
    await this.#fillTo(8);
    const slice = this.#slice(8);
    const view = new DataView(slice.buffer, slice.byteOffset, slice.byteLength);
    return view.getBigUint64(0) & 0x3fffffffffffffffn;
  }
  // Returns false if there is more data to read, blocking if it hasn't been received yet.
  async done() {
    if (this.#buffer.byteLength > 0) return false;
    return !await this.#fill();
  }
  stop(reason) {
    this.#reader?.cancel(reason).catch(() => void 0);
  }
  get closed() {
    return this.#reader?.closed ?? Promise.resolve();
  }
};
var Writer = class _Writer {
  #writer;
  #stream;
  // Scratch buffer for writing varints.
  // Fixed at 8 bytes.
  #scratch;
  constructor(stream) {
    this.#stream = stream;
    this.#scratch = new ArrayBuffer(8);
    this.#writer = this.#stream.getWriter();
  }
  async bool(v) {
    await this.write(setUint82(this.#scratch, v ? 1 : 0));
  }
  async u8(v) {
    await this.write(setUint82(this.#scratch, v));
  }
  async u16(v) {
    await this.write(setUint162(this.#scratch, v));
  }
  async i32(v) {
    if (Math.abs(v) > MAX_U31) {
      throw new Error(\`overflow, value larger than 32-bits: \${v.toString()}\`);
    }
    await this.write(setInt32(this.#scratch, v));
  }
  async u53(v) {
    if (v > MAX_U53) {
      throw new Error(\`overflow, value larger than 53-bits: \${v.toString()}\`);
    }
    await this.write(encodeTo(this.#scratch, v));
  }
  async u62(v) {
    await this.write(encodeTo(this.#scratch, v));
  }
  async write(v) {
    await this.#writer.write(v);
  }
  async string(str) {
    const data = new TextEncoder().encode(str);
    await this.u53(data.byteLength);
    await this.write(data);
  }
  close() {
    this.#writer.close().catch(() => void 0);
  }
  get closed() {
    return this.#writer.closed;
  }
  reset(reason) {
    this.#writer.abort(reason).catch(() => void 0);
  }
  static async open(quic) {
    const writable = await quic.createUnidirectionalStream();
    return new _Writer(writable);
  }
};
function setUint82(dst, v) {
  const buffer = new Uint8Array(dst, 0, 1);
  buffer[0] = v;
  return buffer;
}
function setUint162(dst, v) {
  const view = new DataView(dst, 0, 2);
  view.setUint16(0, v);
  return new Uint8Array(view.buffer, view.byteOffset, view.byteLength);
}
function setInt32(dst, v) {
  const view = new DataView(dst, 0, 4);
  view.setInt32(0, v);
  return new Uint8Array(view.buffer, view.byteOffset, view.byteLength);
}

// ../lite/src/util/error.ts
function unreachable(value) {
  throw new Error(\`unreachable: \${value}\`);
}

// ../../node_modules/.bun/async-mutex@0.5.0/node_modules/async-mutex/index.mjs
var E_TIMEOUT = new Error("timeout while waiting for mutex to become available");
var E_ALREADY_LOCKED = new Error("mutex already locked");
var E_CANCELED = new Error("request for lock canceled");

// ../lite/src/ietf/message.ts
async function encode3(writer, f) {
  let scratch = new Uint8Array();
  const temp = new Writer(
    new WritableStream({
      write(chunk) {
        const needed = scratch.byteLength + chunk.byteLength;
        if (needed > scratch.buffer.byteLength) {
          const capacity = Math.max(needed, scratch.buffer.byteLength * 2);
          const newBuffer = new ArrayBuffer(capacity);
          const newScratch = new Uint8Array(newBuffer, 0, needed);
          newScratch.set(scratch);
          newScratch.set(chunk, scratch.byteLength);
          scratch = newScratch;
        } else {
          scratch = new Uint8Array(scratch.buffer, 0, needed);
          scratch.set(chunk, needed - chunk.byteLength);
        }
      }
    })
  );
  try {
    await f(temp);
  } finally {
    temp.close();
  }
  await temp.closed;
  if (scratch.byteLength > 65535) {
    throw new Error(\`Message too large: \${scratch.byteLength} bytes (max 65535)\`);
  }
  await writer.u16(scratch.byteLength);
  await writer.write(scratch);
}
async function decode3(reader, f) {
  const size = await reader.u16();
  const data = await reader.read(size);
  const limit = new Reader(void 0, data);
  const msg = await f(limit);
  if (!await limit.done()) {
    throw new Error("Message decoding consumed too few bytes");
  }
  return msg;
}

// ../lite/src/ietf/fetch.ts
var Fetch = class _Fetch {
  static id = 22;
  requestId;
  trackNamespace;
  trackName;
  subscriberPriority;
  groupOrder;
  startGroup;
  startObject;
  endGroup;
  endObject;
  constructor({
    requestId,
    trackNamespace,
    trackName,
    subscriberPriority,
    groupOrder,
    startGroup,
    startObject,
    endGroup,
    endObject
  }) {
    this.requestId = requestId;
    this.trackNamespace = trackNamespace;
    this.trackName = trackName;
    this.subscriberPriority = subscriberPriority;
    this.groupOrder = groupOrder;
    this.startGroup = startGroup;
    this.startObject = startObject;
    this.endGroup = endGroup;
    this.endObject = endObject;
  }
  async #encode(_w) {
    throw new Error("FETCH messages are not supported");
  }
  async encode(w, _version) {
    return encode3(w, this.#encode.bind(this));
  }
  static async decode(r, _version) {
    return decode3(r, _Fetch.#decode);
  }
  static async #decode(_r) {
    throw new Error("FETCH messages are not supported");
  }
};
var FetchOk = class _FetchOk {
  static id = 24;
  requestId;
  constructor({ requestId }) {
    this.requestId = requestId;
  }
  async #encode(_w) {
    throw new Error("FETCH_OK messages are not supported");
  }
  async encode(w, _version) {
    return encode3(w, this.#encode.bind(this));
  }
  static async decode(r, _version) {
    return decode3(r, _FetchOk.#decode);
  }
  static async #decode(_r) {
    throw new Error("FETCH_OK messages are not supported");
  }
};
var FetchError = class _FetchError {
  static id = 25;
  requestId;
  errorCode;
  reasonPhrase;
  constructor({
    requestId,
    errorCode,
    reasonPhrase
  }) {
    this.requestId = requestId;
    this.errorCode = errorCode;
    this.reasonPhrase = reasonPhrase;
  }
  async #encode(_w) {
    throw new Error("FETCH_ERROR messages are not supported");
  }
  async encode(w, _version) {
    return encode3(w, this.#encode.bind(this));
  }
  static async decode(r, _version) {
    return decode3(r, _FetchError.#decode);
  }
  static async #decode(_r) {
    throw new Error("FETCH_ERROR messages are not supported");
  }
};
var FetchCancel = class _FetchCancel {
  static id = 23;
  requestId;
  constructor({ requestId }) {
    this.requestId = requestId;
  }
  async #encode(_w) {
    throw new Error("FETCH_CANCEL messages are not supported");
  }
  async encode(w, _version) {
    return encode3(w, this.#encode.bind(this));
  }
  static async decode(r, _version) {
    return decode3(r, _FetchCancel.#decode);
  }
  static async #decode(_r) {
    throw new Error("FETCH_CANCEL messages are not supported");
  }
};

// ../lite/src/ietf/goaway.ts
var GoAway = class _GoAway {
  static id = 16;
  newSessionUri;
  constructor({ newSessionUri }) {
    this.newSessionUri = newSessionUri;
  }
  async #encode(w) {
    await w.string(this.newSessionUri);
  }
  async encode(w, _version) {
    return encode3(w, this.#encode.bind(this));
  }
  static async decode(r, _version) {
    return decode3(r, _GoAway.#decode);
  }
  static async #decode(r) {
    const newSessionUri = await r.string();
    return new _GoAway({ newSessionUri });
  }
};

// ../lite/src/ietf/namespace.ts
async function encode4(w, namespace) {
  const parts = namespace.split("/");
  await w.u53(parts.length);
  for (const part of parts) {
    await w.string(part);
  }
}
async function decode4(r) {
  const parts = [];
  const count = await r.u53();
  for (let i = 0; i < count; i++) {
    parts.push(await r.string());
  }
  return from(...parts);
}

// ../lite/src/ietf/version.ts
var Version = {
  /**
   * draft-ietf-moq-transport-07
   * https://www.ietf.org/archive/id/draft-ietf-moq-transport-07.txt
   */
  DRAFT_07: 4278190087,
  /**
   * draft-ietf-moq-transport-14
   * https://www.ietf.org/archive/id/draft-ietf-moq-transport-14.txt
   */
  DRAFT_14: 4278190094,
  /**
   * draft-ietf-moq-transport-15
   * https://www.ietf.org/archive/id/draft-ietf-moq-transport-15.txt
   */
  DRAFT_15: 4278190095,
  /**
   * draft-ietf-moq-transport-16
   * https://www.ietf.org/archive/id/draft-ietf-moq-transport-16.txt
   */
  DRAFT_16: 4278190096
};

// ../lite/src/ietf/parameters.ts
var Parameters = class _Parameters {
  vars;
  bytes;
  constructor() {
    this.vars = /* @__PURE__ */ new Map();
    this.bytes = /* @__PURE__ */ new Map();
  }
  get size() {
    return this.vars.size + this.bytes.size;
  }
  setBytes(id, value) {
    if (id % 2n !== 1n) {
      throw new Error(\`invalid parameter id: \${id.toString()}, must be odd\`);
    }
    this.bytes.set(id, value);
  }
  setVarint(id, value) {
    if (id % 2n !== 0n) {
      throw new Error(\`invalid parameter id: \${id.toString()}, must be even\`);
    }
    this.vars.set(id, value);
  }
  getBytes(id) {
    if (id % 2n !== 1n) {
      throw new Error(\`invalid parameter id: \${id.toString()}, must be odd\`);
    }
    return this.bytes.get(id);
  }
  getVarint(id) {
    if (id % 2n !== 0n) {
      throw new Error(\`invalid parameter id: \${id.toString()}, must be even\`);
    }
    return this.vars.get(id);
  }
  removeBytes(id) {
    if (id % 2n !== 1n) {
      throw new Error(\`invalid parameter id: \${id.toString()}, must be odd\`);
    }
    return this.bytes.delete(id);
  }
  removeVarint(id) {
    if (id % 2n !== 0n) {
      throw new Error(\`invalid parameter id: \${id.toString()}, must be even\`);
    }
    return this.vars.delete(id);
  }
  async encode(w, version) {
    await w.u53(this.vars.size + this.bytes.size);
    if (version === Version.DRAFT_16) {
      const all = [];
      for (const id of this.vars.keys()) all.push({ key: id, isVar: true });
      for (const id of this.bytes.keys()) all.push({ key: id, isVar: false });
      all.sort((a, b) => a.key < b.key ? -1 : a.key > b.key ? 1 : 0);
      let prevId = 0n;
      for (let i = 0; i < all.length; i++) {
        const { key, isVar } = all[i];
        const delta = i === 0 ? key : key - prevId;
        prevId = key;
        await w.u62(delta);
        if (isVar) {
          await w.u62(this.vars.get(key));
        } else {
          const value = this.bytes.get(key);
          await w.u53(value.length);
          await w.write(value);
        }
      }
    } else {
      for (const [id, value] of this.vars) {
        await w.u62(id);
        await w.u62(value);
      }
      for (const [id, value] of this.bytes) {
        await w.u62(id);
        await w.u53(value.length);
        await w.write(value);
      }
    }
  }
  static async decode(r, version) {
    const count = await r.u53();
    const params = new _Parameters();
    let prevType = 0n;
    for (let i = 0; i < count; i++) {
      let id;
      if (version === Version.DRAFT_16) {
        const delta = await r.u62();
        id = i === 0 ? delta : prevType + delta;
        prevType = id;
      } else {
        id = await r.u62();
      }
      if (id % 2n === 0n) {
        if (params.vars.has(id)) {
          throw new Error(\`duplicate parameter id: \${id.toString()}\`);
        }
        const varint = await r.u62();
        params.setVarint(id, varint);
      } else {
        if (params.bytes.has(id)) {
          throw new Error(\`duplicate parameter id: \${id.toString()}\`);
        }
        const size = await r.u53();
        const bytes = await r.read(size);
        params.setBytes(id, bytes);
      }
    }
    return params;
  }
};
var MSG_PARAM_DELIVERY_TIMEOUT = 0x02n;
var MSG_PARAM_MAX_CACHE_DURATION = 0x04n;
var MSG_PARAM_EXPIRES = 0x08n;
var MSG_PARAM_PUBLISHER_PRIORITY = 0x0en;
var MSG_PARAM_FORWARD = 0x10n;
var MSG_PARAM_SUBSCRIBER_PRIORITY = 0x20n;
var MSG_PARAM_GROUP_ORDER = 0x22n;
var MSG_PARAM_LARGEST_OBJECT = 0x09n;
var MSG_PARAM_SUBSCRIPTION_FILTER = 0x21n;
var MessageParameters = class _MessageParameters {
  vars;
  bytes;
  constructor() {
    this.vars = /* @__PURE__ */ new Map();
    this.bytes = /* @__PURE__ */ new Map();
  }
  // --- Varint accessors ---
  get subscriberPriority() {
    const v = this.vars.get(MSG_PARAM_SUBSCRIBER_PRIORITY);
    return v !== void 0 ? Number(v) : void 0;
  }
  set subscriberPriority(v) {
    this.vars.set(MSG_PARAM_SUBSCRIBER_PRIORITY, BigInt(v));
  }
  get groupOrder() {
    const v = this.vars.get(MSG_PARAM_GROUP_ORDER);
    return v !== void 0 ? Number(v) : void 0;
  }
  set groupOrder(v) {
    this.vars.set(MSG_PARAM_GROUP_ORDER, BigInt(v));
  }
  get forward() {
    const v = this.vars.get(MSG_PARAM_FORWARD);
    return v !== void 0 ? v !== 0n : void 0;
  }
  set forward(v) {
    this.vars.set(MSG_PARAM_FORWARD, v ? 1n : 0n);
  }
  get publisherPriority() {
    const v = this.vars.get(MSG_PARAM_PUBLISHER_PRIORITY);
    return v !== void 0 ? Number(v) : void 0;
  }
  set publisherPriority(v) {
    this.vars.set(MSG_PARAM_PUBLISHER_PRIORITY, BigInt(v));
  }
  get expires() {
    return this.vars.get(MSG_PARAM_EXPIRES);
  }
  set expires(v) {
    this.vars.set(MSG_PARAM_EXPIRES, v);
  }
  get deliveryTimeout() {
    return this.vars.get(MSG_PARAM_DELIVERY_TIMEOUT);
  }
  set deliveryTimeout(v) {
    this.vars.set(MSG_PARAM_DELIVERY_TIMEOUT, v);
  }
  get maxCacheDuration() {
    return this.vars.get(MSG_PARAM_MAX_CACHE_DURATION);
  }
  set maxCacheDuration(v) {
    this.vars.set(MSG_PARAM_MAX_CACHE_DURATION, v);
  }
  // --- Bytes accessors ---
  get largest() {
    const data = this.bytes.get(MSG_PARAM_LARGEST_OBJECT);
    if (!data || data.length === 0) return void 0;
    const [groupId, rest] = decode2(data);
    const [objectId] = decode2(rest);
    return { groupId: BigInt(groupId), objectId: BigInt(objectId) };
  }
  set largest(v) {
    const buf1 = encode2(Number(v.groupId));
    const buf2 = encode2(Number(v.objectId));
    const combined = new Uint8Array(buf1.length + buf2.length);
    combined.set(buf1, 0);
    combined.set(buf2, buf1.length);
    this.bytes.set(MSG_PARAM_LARGEST_OBJECT, combined);
  }
  get subscriptionFilter() {
    const data = this.bytes.get(MSG_PARAM_SUBSCRIPTION_FILTER);
    if (!data || data.length === 0) return void 0;
    return data[0];
  }
  set subscriptionFilter(v) {
    this.bytes.set(MSG_PARAM_SUBSCRIPTION_FILTER, new Uint8Array([v]));
  }
  async encode(w, version) {
    await w.u53(this.vars.size + this.bytes.size);
    if (version === Version.DRAFT_16) {
      const all = [];
      for (const id of this.vars.keys()) all.push({ key: id, isVar: true });
      for (const id of this.bytes.keys()) all.push({ key: id, isVar: false });
      all.sort((a, b) => a.key < b.key ? -1 : a.key > b.key ? 1 : 0);
      let prevId = 0n;
      for (let i = 0; i < all.length; i++) {
        const { key, isVar } = all[i];
        const delta = i === 0 ? key : key - prevId;
        prevId = key;
        await w.u62(delta);
        if (isVar) {
          await w.u62(this.vars.get(key));
        } else {
          const value = this.bytes.get(key);
          await w.u53(value.length);
          await w.write(value);
        }
      }
    } else {
      for (const [id, value] of this.vars) {
        await w.u62(id);
        await w.u62(value);
      }
      for (const [id, value] of this.bytes) {
        await w.u62(id);
        await w.u53(value.length);
        await w.write(value);
      }
    }
  }
  static async decode(r, version) {
    const count = await r.u53();
    const params = new _MessageParameters();
    let prevType = 0n;
    for (let i = 0; i < count; i++) {
      let id;
      if (version === Version.DRAFT_16) {
        const delta = await r.u62();
        id = i === 0 ? delta : prevType + delta;
        prevType = id;
      } else {
        id = await r.u62();
      }
      if (id % 2n === 0n) {
        if (params.vars.has(id)) {
          throw new Error(\`duplicate message parameter id: \${id.toString()}\`);
        }
        const varint = await r.u62();
        params.vars.set(id, varint);
      } else {
        if (params.bytes.has(id)) {
          throw new Error(\`duplicate message parameter id: \${id.toString()}\`);
        }
        const size = await r.u53();
        const bytes = await r.read(size);
        params.bytes.set(id, bytes);
      }
    }
    return params;
  }
};

// ../lite/src/ietf/publish.ts
var Publish = class _Publish {
  static id = 29;
  requestId;
  trackNamespace;
  trackName;
  trackAlias;
  groupOrder;
  contentExists;
  largest;
  forward;
  constructor({
    requestId,
    trackNamespace,
    trackName,
    trackAlias,
    groupOrder,
    contentExists,
    largest,
    forward
  }) {
    this.requestId = requestId;
    this.trackNamespace = trackNamespace;
    this.trackName = trackName;
    this.trackAlias = trackAlias;
    this.groupOrder = groupOrder;
    this.contentExists = contentExists;
    this.largest = largest;
    this.forward = forward;
  }
  async #encode(w, version) {
    await w.u62(this.requestId);
    await encode4(w, this.trackNamespace);
    await w.string(this.trackName);
    await w.u62(this.trackAlias);
    if (version === Version.DRAFT_15 || version === Version.DRAFT_16) {
      const params = new MessageParameters();
      params.groupOrder = this.groupOrder;
      params.forward = this.forward;
      if (this.largest) {
        params.largest = this.largest;
      }
      await params.encode(w, version);
    } else if (version === Version.DRAFT_14) {
      await w.u8(this.groupOrder);
      await w.bool(this.contentExists);
      if (this.contentExists !== !!this.largest) {
        throw new Error("contentExists and largest must both be true or false");
      }
      if (this.largest) {
        await w.u62(this.largest.groupId);
        await w.u62(this.largest.objectId);
      }
      await w.bool(this.forward);
      await w.u53(0);
    } else {
      unreachable(version);
    }
  }
  async encode(w, version) {
    return encode3(w, (mw) => this.#encode(mw, version));
  }
  static async decode(r, version) {
    return decode3(r, (mr) => _Publish.#decode(mr, version));
  }
  static async #decode(r, version) {
    const requestId = await r.u62();
    const trackNamespace = await decode4(r);
    const trackName = await r.string();
    const trackAlias = await r.u62();
    if (version === Version.DRAFT_15 || version === Version.DRAFT_16) {
      const params = await MessageParameters.decode(r, version);
      const groupOrder = params.groupOrder ?? 2;
      const forward = params.forward ?? true;
      const largest = params.largest;
      return new _Publish({
        requestId,
        trackNamespace,
        trackName,
        trackAlias,
        groupOrder,
        contentExists: !!largest,
        largest,
        forward
      });
    } else if (version === Version.DRAFT_14) {
      const groupOrder = await r.u8();
      const contentExists = await r.bool();
      const largest = contentExists ? { groupId: await r.u62(), objectId: await r.u62() } : void 0;
      const forward = await r.bool();
      await Parameters.decode(r, version);
      return new _Publish({
        requestId,
        trackNamespace,
        trackName,
        trackAlias,
        groupOrder,
        contentExists,
        largest,
        forward
      });
    } else {
      unreachable(version);
    }
  }
};
var PublishOk = class _PublishOk {
  static id = 30;
  async #encode(_w) {
    throw new Error("PUBLISH_OK messages are not supported");
  }
  async encode(w, _version) {
    return encode3(w, this.#encode.bind(this));
  }
  static async decode(r, _version) {
    return decode3(r, _PublishOk.#decode);
  }
  static async #decode(_r) {
    throw new Error("PUBLISH_OK messages are not supported");
  }
};
var PublishError = class _PublishError {
  static id = 31;
  requestId;
  errorCode;
  reasonPhrase;
  constructor({
    requestId,
    errorCode,
    reasonPhrase
  }) {
    this.requestId = requestId;
    this.errorCode = errorCode;
    this.reasonPhrase = reasonPhrase;
  }
  async #encode(w) {
    await w.u62(this.requestId);
    await w.u62(BigInt(this.errorCode));
    await w.string(this.reasonPhrase);
  }
  async encode(w, _version) {
    return encode3(w, this.#encode.bind(this));
  }
  static async decode(r, _version) {
    return decode3(r, _PublishError.#decode);
  }
  static async #decode(r) {
    const requestId = await r.u62();
    const errorCode = Number(await r.u62());
    const reasonPhrase = await r.string();
    return new _PublishError({ requestId, errorCode, reasonPhrase });
  }
};
var PublishDone = class _PublishDone {
  static id = 11;
  requestId;
  statusCode;
  reasonPhrase;
  constructor({
    requestId,
    statusCode,
    reasonPhrase
  }) {
    this.requestId = requestId;
    this.statusCode = statusCode;
    this.reasonPhrase = reasonPhrase;
  }
  async #encode(w) {
    await w.u62(this.requestId);
    await w.u62(BigInt(this.statusCode));
    await w.u62(BigInt(0));
    await w.string(this.reasonPhrase);
  }
  async encode(w, _version) {
    return encode3(w, this.#encode.bind(this));
  }
  static async decode(r, _version) {
    return decode3(r, _PublishDone.#decode);
  }
  static async #decode(r) {
    const requestId = await r.u62();
    const statusCode = Number(await r.u62());
    await r.u62();
    const reasonPhrase = await r.string();
    return new _PublishDone({ requestId, statusCode, reasonPhrase });
  }
};

// ../lite/src/ietf/publish_namespace.ts
var PublishNamespace = class _PublishNamespace {
  static id = 6;
  requestId;
  trackNamespace;
  constructor({ requestId, trackNamespace }) {
    this.requestId = requestId;
    this.trackNamespace = trackNamespace;
  }
  async #encode(w, _version) {
    await w.u62(this.requestId);
    await encode4(w, this.trackNamespace);
    await w.u53(0);
  }
  async encode(w, version) {
    return encode3(w, (wr) => this.#encode(wr, version));
  }
  static async decode(r, version) {
    return decode3(r, (rd) => _PublishNamespace.#decode(rd, version));
  }
  static async #decode(r, version) {
    const requestId = await r.u62();
    const trackNamespace = await decode4(r);
    await Parameters.decode(r, version);
    return new _PublishNamespace({ requestId, trackNamespace });
  }
};
var PublishNamespaceOk = class _PublishNamespaceOk {
  static id = 7;
  requestId;
  constructor({ requestId }) {
    this.requestId = requestId;
  }
  async #encode(w) {
    await w.u62(this.requestId);
  }
  async encode(w, _version) {
    return encode3(w, this.#encode.bind(this));
  }
  static async decode(r, _version) {
    return decode3(r, _PublishNamespaceOk.#decode);
  }
  static async #decode(r) {
    const requestId = await r.u62();
    return new _PublishNamespaceOk({ requestId });
  }
};
var PublishNamespaceError = class _PublishNamespaceError {
  static id = 8;
  requestId;
  errorCode;
  reasonPhrase;
  constructor({
    requestId,
    errorCode,
    reasonPhrase
  }) {
    this.requestId = requestId;
    this.errorCode = errorCode;
    this.reasonPhrase = reasonPhrase;
  }
  async #encode(w) {
    await w.u62(this.requestId);
    await w.u62(BigInt(this.errorCode));
    await w.string(this.reasonPhrase);
  }
  async encode(w, _version) {
    return encode3(w, this.#encode.bind(this));
  }
  static async decode(r, _version) {
    return decode3(r, _PublishNamespaceError.#decode);
  }
  static async #decode(r) {
    const requestId = await r.u62();
    const errorCode = Number(await r.u62());
    const reasonPhrase = await r.string();
    return new _PublishNamespaceError({ requestId, errorCode, reasonPhrase });
  }
};
var PublishNamespaceCancel = class _PublishNamespaceCancel {
  static id = 12;
  trackNamespace;
  requestId;
  // v16: uses request_id instead of track_namespace
  errorCode;
  reasonPhrase;
  constructor({
    trackNamespace = "",
    errorCode = 0,
    reasonPhrase = "",
    requestId = 0n
  } = {}) {
    this.trackNamespace = trackNamespace;
    this.requestId = requestId;
    this.errorCode = errorCode;
    this.reasonPhrase = reasonPhrase;
  }
  async #encode(w, version) {
    if (version === Version.DRAFT_16) {
      await w.u62(this.requestId);
    } else {
      await encode4(w, this.trackNamespace);
    }
    await w.u62(BigInt(this.errorCode));
    await w.string(this.reasonPhrase);
  }
  async encode(w, version) {
    return encode3(w, (wr) => this.#encode(wr, version));
  }
  static async decode(r, version) {
    return decode3(r, (rd) => _PublishNamespaceCancel.#decode(rd, version));
  }
  static async #decode(r, version) {
    let trackNamespace = "";
    let requestId = 0n;
    if (version === Version.DRAFT_16) {
      requestId = await r.u62();
    } else {
      trackNamespace = await decode4(r);
    }
    const errorCode = Number(await r.u62());
    const reasonPhrase = await r.string();
    return new _PublishNamespaceCancel({ trackNamespace, errorCode, reasonPhrase, requestId });
  }
};
var PublishNamespaceDone = class _PublishNamespaceDone {
  static id = 9;
  trackNamespace;
  requestId;
  // v16: uses request_id instead of track_namespace
  constructor({
    trackNamespace = "",
    requestId = 0n
  } = {}) {
    this.trackNamespace = trackNamespace;
    this.requestId = requestId;
  }
  async #encode(w, version) {
    if (version === Version.DRAFT_16) {
      await w.u62(this.requestId);
    } else {
      await encode4(w, this.trackNamespace);
    }
  }
  async encode(w, version) {
    return encode3(w, (wr) => this.#encode(wr, version));
  }
  static async decode(r, version) {
    return decode3(r, (rd) => _PublishNamespaceDone.#decode(rd, version));
  }
  static async #decode(r, version) {
    if (version === Version.DRAFT_16) {
      const requestId = await r.u62();
      return new _PublishNamespaceDone({ requestId });
    }
    const trackNamespace = await decode4(r);
    return new _PublishNamespaceDone({ trackNamespace });
  }
};

// ../lite/src/ietf/request.ts
var MaxRequestId = class _MaxRequestId {
  static id = 21;
  requestId;
  constructor({ requestId }) {
    this.requestId = requestId;
  }
  async #encode(w) {
    await w.u62(this.requestId);
  }
  async encode(w, _version) {
    return encode3(w, this.#encode.bind(this));
  }
  static async #decode(r) {
    return new _MaxRequestId({ requestId: await r.u62() });
  }
  static async decode(r, _version) {
    return decode3(r, _MaxRequestId.#decode);
  }
};
var RequestsBlocked = class _RequestsBlocked {
  static id = 26;
  requestId;
  constructor({ requestId }) {
    this.requestId = requestId;
  }
  async #encode(w) {
    await w.u62(this.requestId);
  }
  async encode(w, _version) {
    return encode3(w, this.#encode.bind(this));
  }
  static async #decode(r) {
    return new _RequestsBlocked({ requestId: await r.u62() });
  }
  static async decode(r, _version) {
    return decode3(r, _RequestsBlocked.#decode);
  }
};
var RequestOk = class _RequestOk {
  static id = 7;
  requestId;
  parameters;
  constructor({
    requestId,
    parameters = new MessageParameters()
  }) {
    this.requestId = requestId;
    this.parameters = parameters;
  }
  async #encode(w, version) {
    await w.u62(this.requestId);
    await this.parameters.encode(w, version);
  }
  async encode(w, version) {
    return encode3(w, (wr) => this.#encode(wr, version));
  }
  static async #decode(r, version) {
    const requestId = await r.u62();
    const parameters = await MessageParameters.decode(r, version);
    return new _RequestOk({ requestId, parameters });
  }
  static async decode(r, version) {
    return decode3(r, (rd) => _RequestOk.#decode(rd, version));
  }
};
var RequestError = class _RequestError {
  static id = 5;
  requestId;
  errorCode;
  reasonPhrase;
  retryInterval;
  constructor({
    requestId,
    errorCode,
    reasonPhrase,
    retryInterval = 0n
  }) {
    this.requestId = requestId;
    this.errorCode = errorCode;
    this.reasonPhrase = reasonPhrase;
    this.retryInterval = retryInterval;
  }
  async #encode(w, version) {
    await w.u62(this.requestId);
    await w.u62(BigInt(this.errorCode));
    if (version === Version.DRAFT_16) {
      await w.u62(this.retryInterval);
    }
    await w.string(this.reasonPhrase);
  }
  async encode(w, version) {
    return encode3(w, (wr) => this.#encode(wr, version));
  }
  static async #decode(r, version) {
    const requestId = await r.u62();
    const errorCode = Number(await r.u62());
    const retryInterval = version === Version.DRAFT_16 ? await r.u62() : 0n;
    const reasonPhrase = await r.string();
    return new _RequestError({ requestId, errorCode, reasonPhrase, retryInterval });
  }
  static async decode(r, version) {
    return decode3(r, (rd) => _RequestError.#decode(rd, version));
  }
};

// ../lite/src/ietf/setup.ts
var MAX_VERSIONS = 128;
var ClientSetup = class _ClientSetup {
  static id = 32;
  versions;
  parameters;
  constructor({ versions, parameters = new Parameters() }) {
    this.versions = versions;
    this.parameters = parameters;
  }
  async #encode(w, version) {
    if (version === Version.DRAFT_15 || version === Version.DRAFT_16) {
      await this.parameters.encode(w, version);
    } else if (version === Version.DRAFT_14) {
      await w.u53(this.versions.length);
      for (const v of this.versions) {
        await w.u53(v);
      }
      await this.parameters.encode(w, version);
    } else {
      unreachable(version);
    }
  }
  async encode(w, version) {
    return encode3(w, (mw) => this.#encode(mw, version));
  }
  static async #decode(r, version) {
    if (version === Version.DRAFT_15 || version === Version.DRAFT_16) {
      const parameters = await Parameters.decode(r, version);
      return new _ClientSetup({ versions: [version], parameters });
    } else if (version === Version.DRAFT_14) {
      const numVersions = await r.u53();
      if (numVersions > MAX_VERSIONS) {
        throw new Error(\`too many versions: \${numVersions}\`);
      }
      const supportedVersions = [];
      for (let i = 0; i < numVersions; i++) {
        const v = await r.u53();
        supportedVersions.push(v);
      }
      const parameters = await Parameters.decode(r, version);
      return new _ClientSetup({ versions: supportedVersions, parameters });
    } else {
      unreachable(version);
    }
  }
  static async decode(r, version) {
    return decode3(r, (mr) => _ClientSetup.#decode(mr, version));
  }
};
var ServerSetup = class _ServerSetup {
  static id = 33;
  version;
  parameters;
  constructor({ version, parameters = new Parameters() }) {
    this.version = version;
    this.parameters = parameters;
  }
  async #encode(w, version) {
    if (version === Version.DRAFT_15 || version === Version.DRAFT_16) {
      await this.parameters.encode(w, version);
    } else if (version === Version.DRAFT_14) {
      await w.u53(this.version);
      await this.parameters.encode(w, version);
    } else {
      unreachable(version);
    }
  }
  async encode(w, version) {
    return encode3(w, (mw) => this.#encode(mw, version));
  }
  static async #decode(r, version) {
    if (version === Version.DRAFT_15 || version === Version.DRAFT_16) {
      const parameters = await Parameters.decode(r, version);
      return new _ServerSetup({ version, parameters });
    } else if (version === Version.DRAFT_14) {
      const selectedVersion = await r.u53();
      const parameters = await Parameters.decode(r, version);
      return new _ServerSetup({ version: selectedVersion, parameters });
    } else {
      unreachable(version);
    }
  }
  static async decode(r, version) {
    return decode3(r, (mr) => _ServerSetup.#decode(mr, version));
  }
};

// ../lite/src/ietf/subscribe.ts
var GROUP_ORDER = 2;
var Subscribe = class _Subscribe {
  static id = 3;
  requestId;
  trackNamespace;
  trackName;
  subscriberPriority;
  constructor({
    requestId,
    trackNamespace,
    trackName,
    subscriberPriority
  }) {
    this.requestId = requestId;
    this.trackNamespace = trackNamespace;
    this.trackName = trackName;
    this.subscriberPriority = subscriberPriority;
  }
  async #encode(w, version) {
    await w.u62(this.requestId);
    await encode4(w, this.trackNamespace);
    await w.string(this.trackName);
    if (version === Version.DRAFT_15 || version === Version.DRAFT_16) {
      const params = new MessageParameters();
      params.subscriberPriority = this.subscriberPriority;
      params.groupOrder = GROUP_ORDER;
      params.forward = true;
      params.subscriptionFilter = 2;
      await params.encode(w, version);
    } else if (version === Version.DRAFT_14) {
      await w.u8(this.subscriberPriority);
      await w.u8(GROUP_ORDER);
      await w.bool(true);
      await w.u53(2);
      await w.u53(0);
    } else {
      unreachable(version);
    }
  }
  async encode(w, version) {
    return encode3(w, (mw) => this.#encode(mw, version));
  }
  static async decode(r, version) {
    return decode3(r, (mr) => _Subscribe.#decode(mr, version));
  }
  static async #decode(r, version) {
    const requestId = await r.u62();
    const trackNamespace = await decode4(r);
    const trackName = await r.string();
    if (version === Version.DRAFT_15 || version === Version.DRAFT_16) {
      const params = await MessageParameters.decode(r, version);
      const subscriberPriority = params.subscriberPriority ?? 128;
      let groupOrder = params.groupOrder ?? GROUP_ORDER;
      if (groupOrder > 2) {
        throw new Error(\`unknown group order: \${groupOrder}\`);
      }
      if (groupOrder === 0) {
        groupOrder = GROUP_ORDER;
      }
      const forward = params.forward ?? true;
      if (!forward) {
        throw new Error(\`unsupported forward value: \${forward}\`);
      }
      const filterType = params.subscriptionFilter ?? 2;
      if (filterType !== 1 && filterType !== 2) {
        throw new Error(\`unsupported filter type: \${filterType}\`);
      }
      return new _Subscribe({ requestId, trackNamespace, trackName, subscriberPriority });
    } else if (version === Version.DRAFT_14) {
      const subscriberPriority = await r.u8();
      let groupOrder = await r.u8();
      if (groupOrder > 2) {
        throw new Error(\`unknown group order: \${groupOrder}\`);
      }
      if (groupOrder === 0) {
        groupOrder = GROUP_ORDER;
      }
      const forward = await r.bool();
      if (!forward) {
        throw new Error(\`unsupported forward value: \${forward}\`);
      }
      const filterType = await r.u53();
      if (filterType !== 1 && filterType !== 2) {
        throw new Error(\`unsupported filter type: \${filterType}\`);
      }
      await Parameters.decode(r, version);
      return new _Subscribe({ requestId, trackNamespace, trackName, subscriberPriority });
    } else {
      unreachable(version);
    }
  }
};
var SubscribeOk = class _SubscribeOk {
  static id = 4;
  requestId;
  trackAlias;
  constructor({ requestId, trackAlias }) {
    this.requestId = requestId;
    this.trackAlias = trackAlias;
  }
  async #encode(w, version) {
    await w.u62(this.requestId);
    await w.u62(this.trackAlias);
    if (version === Version.DRAFT_15 || version === Version.DRAFT_16) {
      const params = new MessageParameters();
      params.groupOrder = GROUP_ORDER;
      await params.encode(w, version);
    } else if (version === Version.DRAFT_14) {
      await w.u62(0n);
      await w.u8(GROUP_ORDER);
      await w.bool(false);
      await w.u53(0);
    } else {
      unreachable(version);
    }
  }
  async encode(w, version) {
    return encode3(w, (mw) => this.#encode(mw, version));
  }
  static async decode(r, version) {
    return decode3(r, (mr) => _SubscribeOk.#decode(mr, version));
  }
  static async #decode(r, version) {
    const requestId = await r.u62();
    const trackAlias = await r.u62();
    if (version === Version.DRAFT_15 || version === Version.DRAFT_16) {
      await MessageParameters.decode(r, version);
    } else if (version === Version.DRAFT_14) {
      const expires = await r.u62();
      if (expires !== BigInt(0)) {
        throw new Error(\`unsupported expires: \${expires}\`);
      }
      await r.u8();
      const contentExists = await r.bool();
      if (contentExists) {
        await r.u62();
        await r.u62();
      }
      await Parameters.decode(r, version);
    } else {
      unreachable(version);
    }
    return new _SubscribeOk({ requestId, trackAlias });
  }
};
var SubscribeError = class _SubscribeError {
  static id = 5;
  requestId;
  errorCode;
  reasonPhrase;
  constructor({
    requestId,
    errorCode,
    reasonPhrase
  }) {
    this.requestId = requestId;
    this.errorCode = errorCode;
    this.reasonPhrase = reasonPhrase;
  }
  async #encode(w) {
    await w.u62(this.requestId);
    await w.u62(BigInt(this.errorCode));
    await w.string(this.reasonPhrase);
  }
  async encode(w, _version) {
    return encode3(w, this.#encode.bind(this));
  }
  static async decode(r, _version) {
    return decode3(r, _SubscribeError.#decode);
  }
  static async #decode(r) {
    const requestId = await r.u62();
    const errorCode = Number(await r.u62());
    const reasonPhrase = await r.string();
    return new _SubscribeError({ requestId, errorCode, reasonPhrase });
  }
};
var Unsubscribe = class _Unsubscribe {
  static id = 10;
  requestId;
  constructor({ requestId }) {
    this.requestId = requestId;
  }
  async #encode(w) {
    await w.u62(this.requestId);
  }
  async encode(w, _version) {
    return encode3(w, this.#encode.bind(this));
  }
  static async decode(r, _version) {
    return decode3(r, _Unsubscribe.#decode);
  }
  static async #decode(r) {
    const requestId = await r.u62();
    return new _Unsubscribe({ requestId });
  }
};

// ../lite/src/ietf/subscribe_namespace.ts
var SubscribeNamespace = class _SubscribeNamespace {
  static id = 17;
  namespace;
  requestId;
  subscribeOptions;
  // v16: default 0x01 (NAMESPACE only)
  constructor({
    namespace,
    requestId,
    subscribeOptions = 1
  }) {
    this.namespace = namespace;
    this.requestId = requestId;
    this.subscribeOptions = subscribeOptions;
  }
  async #encode(w, version) {
    await w.u62(this.requestId);
    await encode4(w, this.namespace);
    if (version === Version.DRAFT_16) {
      await w.u53(this.subscribeOptions);
    }
    await w.u53(0);
  }
  async encode(w, version) {
    return encode3(w, (wr) => this.#encode(wr, version));
  }
  static async decode(r, version) {
    return decode3(r, (rd) => _SubscribeNamespace.#decode(rd, version));
  }
  static async #decode(r, version) {
    const requestId = await r.u62();
    const namespace = await decode4(r);
    let subscribeOptions = 1;
    if (version === Version.DRAFT_16) {
      subscribeOptions = await r.u53();
    }
    await Parameters.decode(r, version);
    return new _SubscribeNamespace({ namespace, requestId, subscribeOptions });
  }
};
var SubscribeNamespaceOk = class _SubscribeNamespaceOk {
  static id = 18;
  requestId;
  constructor({ requestId }) {
    this.requestId = requestId;
  }
  async #encode(w) {
    await w.u62(this.requestId);
  }
  async encode(w, _version) {
    return encode3(w, this.#encode.bind(this));
  }
  static async decode(r, _version) {
    return decode3(r, _SubscribeNamespaceOk.#decode);
  }
  static async #decode(r) {
    const requestId = await r.u62();
    return new _SubscribeNamespaceOk({ requestId });
  }
};
var SubscribeNamespaceError = class _SubscribeNamespaceError {
  static id = 19;
  requestId;
  errorCode;
  reasonPhrase;
  constructor({
    requestId,
    errorCode,
    reasonPhrase
  }) {
    this.requestId = requestId;
    this.errorCode = errorCode;
    this.reasonPhrase = reasonPhrase;
  }
  async #encode(w) {
    await w.u62(this.requestId);
    await w.u62(BigInt(this.errorCode));
    await w.string(this.reasonPhrase);
  }
  async encode(w, _version) {
    return encode3(w, this.#encode.bind(this));
  }
  static async decode(r, _version) {
    return decode3(r, _SubscribeNamespaceError.#decode);
  }
  static async #decode(r) {
    const requestId = await r.u62();
    const errorCode = Number(await r.u62());
    const reasonPhrase = await r.string();
    return new _SubscribeNamespaceError({ requestId, errorCode, reasonPhrase });
  }
};
var UnsubscribeNamespace = class _UnsubscribeNamespace {
  static id = 20;
  requestId;
  constructor({ requestId }) {
    this.requestId = requestId;
  }
  async #encode(w) {
    await w.u62(this.requestId);
  }
  async encode(w, _version) {
    return encode3(w, this.#encode.bind(this));
  }
  static async decode(r, _version) {
    return decode3(r, _UnsubscribeNamespace.#decode);
  }
  static async #decode(r) {
    const requestId = await r.u62();
    return new _UnsubscribeNamespace({ requestId });
  }
};

// ../lite/src/ietf/track.ts
var TrackStatusRequest = class _TrackStatusRequest {
  static id = 13;
  trackNamespace;
  trackName;
  constructor({ trackNamespace, trackName }) {
    this.trackNamespace = trackNamespace;
    this.trackName = trackName;
  }
  async #encode(w) {
    await encode4(w, this.trackNamespace);
    await w.string(this.trackName);
  }
  async encode(w, _version) {
    return encode3(w, this.#encode.bind(this));
  }
  static async decode(r, _version) {
    return decode3(r, _TrackStatusRequest.#decode);
  }
  static async #decode(r) {
    const trackNamespace = await decode4(r);
    const trackName = await r.string();
    return new _TrackStatusRequest({ trackNamespace, trackName });
  }
};
var TrackStatus = class _TrackStatus {
  static id = 14;
  trackNamespace;
  trackName;
  statusCode;
  lastGroupId;
  lastObjectId;
  constructor({
    trackNamespace,
    trackName,
    statusCode,
    lastGroupId,
    lastObjectId
  }) {
    this.trackNamespace = trackNamespace;
    this.trackName = trackName;
    this.statusCode = statusCode;
    this.lastGroupId = lastGroupId;
    this.lastObjectId = lastObjectId;
  }
  async #encode(w) {
    await encode4(w, this.trackNamespace);
    await w.string(this.trackName);
    await w.u62(BigInt(this.statusCode));
    await w.u62(this.lastGroupId);
    await w.u62(this.lastObjectId);
  }
  async encode(w, _version) {
    return encode3(w, this.#encode.bind(this));
  }
  static async decode(r, _version) {
    return decode3(r, _TrackStatus.#decode);
  }
  static async #decode(r) {
    const trackNamespace = await decode4(r);
    const trackName = await r.string();
    const statusCode = Number(await r.u62());
    const lastGroupId = await r.u62();
    const lastObjectId = await r.u62();
    return new _TrackStatus({ trackNamespace, trackName, statusCode, lastGroupId, lastObjectId });
  }
  // Track status codes
  static STATUS_IN_PROGRESS = 0;
  static STATUS_NOT_FOUND = 1;
  static STATUS_NOT_AUTHORIZED = 2;
  static STATUS_ENDED = 3;
};

// ../lite/src/ietf/control.ts
var MessagesV14 = {
  [ClientSetup.id]: ClientSetup,
  [ServerSetup.id]: ServerSetup,
  [Subscribe.id]: Subscribe,
  [SubscribeOk.id]: SubscribeOk,
  [SubscribeError.id]: SubscribeError,
  [PublishNamespace.id]: PublishNamespace,
  [PublishNamespaceOk.id]: PublishNamespaceOk,
  [PublishNamespaceError.id]: PublishNamespaceError,
  [PublishNamespaceDone.id]: PublishNamespaceDone,
  [Unsubscribe.id]: Unsubscribe,
  [PublishDone.id]: PublishDone,
  [PublishNamespaceCancel.id]: PublishNamespaceCancel,
  [TrackStatusRequest.id]: TrackStatusRequest,
  [TrackStatus.id]: TrackStatus,
  [GoAway.id]: GoAway,
  [Fetch.id]: Fetch,
  [FetchCancel.id]: FetchCancel,
  [FetchOk.id]: FetchOk,
  [FetchError.id]: FetchError,
  [SubscribeNamespace.id]: SubscribeNamespace,
  [SubscribeNamespaceOk.id]: SubscribeNamespaceOk,
  [SubscribeNamespaceError.id]: SubscribeNamespaceError,
  [UnsubscribeNamespace.id]: UnsubscribeNamespace,
  [Publish.id]: Publish,
  [PublishOk.id]: PublishOk,
  [PublishError.id]: PublishError,
  [MaxRequestId.id]: MaxRequestId,
  [RequestsBlocked.id]: RequestsBlocked
};
var MessagesV15 = {
  [ClientSetup.id]: ClientSetup,
  [ServerSetup.id]: ServerSetup,
  [Subscribe.id]: Subscribe,
  [SubscribeOk.id]: SubscribeOk,
  [RequestError.id]: RequestError,
  // 0x05 → RequestError instead of SubscribeError
  [PublishNamespace.id]: PublishNamespace,
  [RequestOk.id]: RequestOk,
  // 0x07 → RequestOk instead of PublishNamespaceOk
  [PublishNamespaceDone.id]: PublishNamespaceDone,
  [Unsubscribe.id]: Unsubscribe,
  [PublishDone.id]: PublishDone,
  [PublishNamespaceCancel.id]: PublishNamespaceCancel,
  [TrackStatusRequest.id]: TrackStatusRequest,
  [GoAway.id]: GoAway,
  [Fetch.id]: Fetch,
  [FetchCancel.id]: FetchCancel,
  [FetchOk.id]: FetchOk,
  [SubscribeNamespace.id]: SubscribeNamespace,
  [UnsubscribeNamespace.id]: UnsubscribeNamespace,
  [Publish.id]: Publish,
  [MaxRequestId.id]: MaxRequestId,
  [RequestsBlocked.id]: RequestsBlocked
};
var MessagesV16 = {
  [ClientSetup.id]: ClientSetup,
  [ServerSetup.id]: ServerSetup,
  [Subscribe.id]: Subscribe,
  [SubscribeOk.id]: SubscribeOk,
  [RequestError.id]: RequestError,
  // 0x05 → RequestError
  [PublishNamespace.id]: PublishNamespace,
  [RequestOk.id]: RequestOk,
  // 0x07 → RequestOk
  [PublishNamespaceDone.id]: PublishNamespaceDone,
  [Unsubscribe.id]: Unsubscribe,
  [PublishDone.id]: PublishDone,
  [PublishNamespaceCancel.id]: PublishNamespaceCancel,
  [TrackStatusRequest.id]: TrackStatusRequest,
  [GoAway.id]: GoAway,
  [Fetch.id]: Fetch,
  [FetchCancel.id]: FetchCancel,
  [FetchOk.id]: FetchOk,
  // SubscribeNamespace (0x11) removed — now on bidi stream
  // UnsubscribeNamespace (0x14) removed — now use stream close
  [Publish.id]: Publish,
  [MaxRequestId.id]: MaxRequestId,
  [RequestsBlocked.id]: RequestsBlocked
};

// ../lite/src/time.ts
var time_exports = {};
__export(time_exports, {
  Micro: () => Micro,
  Milli: () => Milli,
  Nano: () => Nano,
  Second: () => Second
});
var Nano = {
  zero: 0,
  fromMicro: (us) => us * 1e3,
  fromMilli: (ms) => ms * 1e6,
  fromSecond: (s) => s * 1e9,
  toMicro: (ns) => ns / 1e3,
  toMilli: (ns) => ns / 1e6,
  toSecond: (ns) => ns / 1e9,
  now: () => performance.now() * 1e6,
  add: (a, b) => a + b,
  sub: (a, b) => a - b,
  mul: (a, b) => a * b,
  div: (a, b) => a / b,
  max: (a, b) => Math.max(a, b),
  min: (a, b) => Math.min(a, b)
};
var Micro = {
  zero: 0,
  fromNano: (ns) => ns / 1e3,
  fromMilli: (ms) => ms * 1e3,
  fromSecond: (s) => s * 1e6,
  toNano: (us) => us * 1e3,
  toMilli: (us) => us / 1e3,
  toSecond: (us) => us / 1e6,
  now: () => performance.now() * 1e3,
  add: (a, b) => a + b,
  sub: (a, b) => a - b,
  mul: (a, b) => a * b,
  div: (a, b) => a / b,
  max: (a, b) => Math.max(a, b),
  min: (a, b) => Math.min(a, b)
};
var Milli = {
  zero: 0,
  fromNano: (ns) => ns / 1e6,
  fromMicro: (us) => us / 1e3,
  fromSecond: (s) => s * 1e3,
  toNano: (ms) => ms * 1e6,
  toMicro: (ms) => ms * 1e3,
  toSecond: (ms) => ms / 1e3,
  now: () => performance.now(),
  add: (a, b) => a + b,
  sub: (a, b) => a - b,
  mul: (a, b) => a * b,
  div: (a, b) => a / b,
  max: (a, b) => Math.max(a, b),
  min: (a, b) => Math.min(a, b)
};
var Second = {
  zero: 0,
  fromNano: (ns) => ns / 1e9,
  fromMicro: (us) => us / 1e6,
  fromMilli: (ms) => ms / 1e3,
  toNano: (s) => s * 1e9,
  toMicro: (s) => s * 1e6,
  toMilli: (s) => s * 1e3,
  now: () => performance.now() / 1e3,
  add: (a, b) => a + b,
  sub: (a, b) => a - b,
  mul: (a, b) => a * b,
  div: (a, b) => a / b,
  max: (a, b) => Math.max(a, b),
  min: (a, b) => Math.min(a, b)
};

// src/audio/ring-buffer.ts
var AudioRingBuffer = class {
  #buffer;
  #writeIndex = 0;
  #readIndex = 0;
  rate;
  channels;
  #stalled = true;
  constructor(props) {
    if (props.channels <= 0) throw new Error("invalid channels");
    if (props.rate <= 0) throw new Error("invalid sample rate");
    if (props.latency <= 0) throw new Error("invalid latency");
    const samples = Math.ceil(props.rate * time_exports.Second.fromMilli(props.latency));
    if (samples === 0) throw new Error("empty buffer");
    this.rate = props.rate;
    this.channels = props.channels;
    this.#buffer = [];
    for (let i = 0; i < this.channels; i++) {
      this.#buffer[i] = new Float32Array(samples);
    }
  }
  get stalled() {
    return this.#stalled;
  }
  get timestamp() {
    return time_exports.Micro.fromSecond(this.#readIndex / this.rate);
  }
  get length() {
    return this.#writeIndex - this.#readIndex;
  }
  get capacity() {
    return this.#buffer[0]?.length;
  }
  resize(latency) {
    const newCapacity = Math.ceil(this.rate * time_exports.Second.fromMilli(latency));
    if (newCapacity === this.capacity) return;
    if (newCapacity === 0) throw new Error("empty buffer");
    const newBuffer = [];
    for (let i = 0; i < this.channels; i++) {
      newBuffer[i] = new Float32Array(newCapacity);
    }
    const samplesToKeep = Math.min(this.length, newCapacity);
    if (samplesToKeep > 0) {
      const copyStart = this.#writeIndex - samplesToKeep;
      for (let channel = 0; channel < this.channels; channel++) {
        const src = this.#buffer[channel];
        const dst = newBuffer[channel];
        for (let i = 0; i < samplesToKeep; i++) {
          const srcPos = (copyStart + i) % src.length;
          const dstPos = i % dst.length;
          dst[dstPos] = src[srcPos];
        }
      }
    }
    this.#buffer = newBuffer;
    this.#readIndex = this.#writeIndex - samplesToKeep;
    this.#stalled = true;
  }
  write(timestamp, data) {
    if (data.length !== this.channels) throw new Error("wrong number of channels");
    let start = Math.round(time_exports.Second.fromMicro(timestamp) * this.rate);
    let samples = data[0].length;
    let offset = this.#readIndex - start;
    if (offset > samples) {
      return;
    } else if (offset > 0) {
      samples -= offset;
      start += offset;
    } else {
      offset = 0;
    }
    const end = start + samples;
    const overflow = end - this.#readIndex - this.#buffer[0].length;
    if (overflow >= 0) {
      this.#stalled = false;
      this.#readIndex += overflow;
    }
    if (start > this.#writeIndex) {
      const gapSize = Math.min(start - this.#writeIndex, this.#buffer[0].length);
      if (gapSize === 1) {
        console.warn("floating point inaccuracy detected");
      }
      for (let channel = 0; channel < this.channels; channel++) {
        const dst = this.#buffer[channel];
        for (let i = 0; i < gapSize; i++) {
          const writePos = (this.#writeIndex + i) % dst.length;
          dst[writePos] = 0;
        }
      }
    }
    for (let channel = 0; channel < this.channels; channel++) {
      let src = data[channel];
      src = src.subarray(src.length - samples);
      const dst = this.#buffer[channel];
      if (src.length !== samples) throw new Error("mismatching number of samples");
      for (let i = 0; i < samples; i++) {
        const writePos = (start + i) % dst.length;
        dst[writePos] = src[i];
      }
    }
    if (end > this.#writeIndex) {
      this.#writeIndex = end;
    }
  }
  read(output) {
    if (output.length !== this.channels) throw new Error("wrong number of channels");
    if (this.#stalled) return 0;
    const samples = Math.min(this.#writeIndex - this.#readIndex, output[0].length);
    if (samples === 0) return 0;
    for (let channel = 0; channel < this.channels; channel++) {
      const dst = output[channel];
      const src = this.#buffer[channel];
      if (dst.length !== output[0].length) throw new Error("mismatching number of samples");
      for (let i = 0; i < samples; i++) {
        const readPos = (this.#readIndex + i) % src.length;
        dst[i] = src[readPos];
      }
    }
    this.#readIndex += samples;
    return samples;
  }
};

// src/audio/render-worklet.ts
var Render = class extends AudioWorkletProcessor {
  #buffer;
  #underflow = 0;
  #stateCounter = 0;
  constructor() {
    super();
    this.port.onmessage = (event) => {
      const { type } = event.data;
      if (type === "init") {
        this.#buffer = new AudioRingBuffer(event.data);
        this.#underflow = 0;
      } else if (type === "data") {
        if (!this.#buffer) throw new Error("buffer not initialized");
        this.#buffer.write(event.data.timestamp, event.data.data);
      } else if (type === "latency") {
        if (!this.#buffer) throw new Error("buffer not initialized");
        this.#buffer.resize(event.data.latency);
      } else {
        const exhaustive = type;
        throw new Error(\`unknown message type: \${exhaustive}\`);
      }
    };
  }
  process(_inputs, outputs, _parameters) {
    const output = outputs[0];
    const samplesRead = this.#buffer?.read(output) ?? 0;
    if (samplesRead < output[0].length) {
      this.#underflow += output[0].length - samplesRead;
    } else if (this.#underflow > 0 && this.#buffer) {
      console.warn(\`audio underflow: \${Math.round(1e3 * this.#underflow / this.#buffer.rate)}ms\`);
      this.#underflow = 0;
    }
    this.#stateCounter++;
    if (this.#buffer && this.#stateCounter >= 5) {
      this.#stateCounter = 0;
      const state = {
        type: "state",
        timestamp: this.#buffer.timestamp,
        stalled: this.#buffer.stalled
      };
      this.port.postMessage(state);
    }
    return true;
  }
};
registerProcessor("render", Render);
`,Wl=new Blob([Zl],{type:"application/javascript"}),Hl=URL.createObjectURL(Wl);let Jl=class{source;enabled;#e=new f(void 0);context=this.#e;#t=new f(void 0);root=this.#t;#s=new f(void 0);sampleRate=this.#s;#r=new f(void 0);stats=this.#r;#i=new f(void 0);timestamp=this.#i;#n=new f(!0);stalled=this.#n;#a=new f([]);#o=new f([]);buffered=this.#o;#c=new C;constructor(t,e){this.source=t,this.source.supported.set(Xl),this.enabled=f.from(e?.enabled??!1),this.#c.run(this.#d.bind(this)),this.#c.run(this.#l.bind(this)),this.#c.run(this.#u.bind(this)),this.#c.run(this.#h.bind(this))}#d(t){const e=t.get(this.source.config);if(!e)return;const s=e.sampleRate,r=e.numberOfChannels,i=new AudioContext({latencyHint:"interactive",sampleRate:s});t.set(this.#e,i),t.cleanup(()=>i.close()),t.spawn(async()=>{if(await i.audioWorklet.addModule(Hl),i.state==="closed")return;const n=new AudioWorkletNode(i,"render",{channelCount:r,channelCountMode:"explicit"});t.cleanup(()=>n.disconnect());const a={type:"init",rate:s,channels:r,latency:this.source.sync.latency.peek()};n.port.postMessage(a),n.port.onmessage=o=>{if(o.data.type==="state"){const c=g.fromMicro(o.data.timestamp);this.#i.set(c),this.#n.set(o.data.stalled),this.#y(c)}},t.set(this.#t,n)})}#l(t){const e=t.getAll([this.enabled,this.#e]);if(!e)return;const[s,r]=e;r.resume()}#u(t){const e=t.get(this.#t);if(!e)return;const s={type:"latency",latency:t.get(this.source.sync.latency)};e.port.postMessage(s)}#h(t){if(!t.get(this.enabled))return;const e=t.get(this.source.broadcast);if(!e)return;const s=t.get(this.source.track);if(!s)return;const r=t.get(this.source.config);if(!r)return;const i=t.get(e.active);if(!i)return;const n=i.subscribe(s,pt.audio);t.cleanup(()=>n.close()),r.container.kind==="cmaf"?this.#b(t,n,r):this.#f(t,n,r)}#f(t,e,s){const r=new bt(e,{latency:this.source.sync.latency});t.cleanup(()=>r.close()),t.run(i=>{const n=i.get(r.buffered),a=i.get(this.#a);this.#o.update(()=>Kl(n,a))}),t.spawn(async()=>{if(!await Mr())return;let i=0;const n=new AudioDecoder({output:o=>{if(i++,i<=3){o.close();return}this.#m(o)},error:o=>console.error(o)});t.cleanup(()=>n.close());const a=s.description?ke(s.description):void 0;for(n.configure({...s,description:a});;){const o=await r.next();if(!o)break;const{frame:c}=o;if(!c)continue;this.#r.update(d=>({bytesReceived:(d?.bytesReceived??0)+c.data.byteLength}));const u=new EncodedAudioChunk({type:c.keyframe?"key":"delta",data:c.data,timestamp:c.timestamp});n.decode(u)}})}#b(t,e,s){if(s.container.kind!=="cmaf")return;const{timescale:r}=s.container,i=s.description?ke(s.description):void 0;t.run(n=>{const a=n.get(this.#a);this.#o.update(()=>a)}),t.spawn(async()=>{if(!await Mr())return;const n=new AudioDecoder({output:a=>this.#m(a),error:a=>console.error(a)});for(t.cleanup(()=>n.close()),n.configure({codec:s.codec,sampleRate:s.sampleRate,numberOfChannels:s.numberOfChannels,description:i});;){const a=await e.nextGroup();if(!a)break;t.spawn(async()=>{try{for(;;){const o=await a.readFrame();if(!o)break;const c=en(o,r);for(const u of c){this.#r.update(h=>({bytesReceived:(h?.bytesReceived??0)+u.data.byteLength}));const d=new EncodedAudioChunk({type:u.keyframe?"key":"delta",data:u.data,timestamp:u.timestamp});n.decode(d)}}}finally{a.close()}})}})}#m(t){const e=t.timestamp,s=g.fromMicro(e),r=this.#t.peek();if(!r){t.close();return}const i=t.numberOfFrames/t.sampleRate*1e6,n=g.fromMicro(i),a=g.add(s,n);this.#g(s,a);const o=[];for(let u=0;u<t.numberOfChannels;u++){const d=new Float32Array(t.numberOfFrames);t.copyTo(d,{format:"f32-planar",planeIndex:u}),o.push(d)}const c={type:"data",data:o,timestamp:e};r.port.postMessage(c,c.data.map(u=>u.buffer)),t.close()}#g(t,e){t>e||this.#a.mutate(s=>{for(const r of s)if(t<=r.end+1&&e>=r.start){r.start=g.min(r.start,t),r.end=g.max(r.end,e);return}s.push({start:t,end:e}),s.sort((r,i)=>r.start-i.start)})}#y(t){this.#a.mutate(e=>{for(;e.length>0;){if(e[0].end>=t){e[0].start=g.max(e[0].start,t);break}e.shift()}})}close(){this.#c.close()}};async function Xl(t){const e=t.description?ke(t.description):void 0;return(await AudioDecoder.isConfigSupported({...t,description:e})).supported??!1}function Kl(t,e){if(t.length===0)return e;if(e.length===0)return t;const s=[],r=[...t,...e].sort((i,n)=>i.start-n.start);for(const i of r){const n=s.at(-1);n&&n.end>=i.start?n.end=g.max(n.end,i.end):s.push({...i})}return s}const Lr=.001,ls=.2;class Yl{source;volume;muted;paused;#e=new C;#t=.5;#s=new f(void 0);constructor(e,s){this.source=e,this.volume=f.from(s?.volume??.5),this.muted=f.from(s?.muted??!1),this.paused=f.from(s?.paused??s?.muted??!1),this.#e.run(r=>{r.get(this.muted)?(this.#t=this.volume.peek()||.5,this.volume.set(0)):this.volume.set(this.#t)}),this.#e.run(r=>{const i=!r.get(this.paused)&&!r.get(this.muted);this.source.enabled.set(i)}),this.#e.run(r=>{const i=r.get(this.volume);this.muted.set(i===0)}),this.#e.run(r=>{const i=r.get(this.source.root);if(!i)return;const n=new GainNode(i.context,{gain:r.get(this.volume)});i.connect(n),r.set(this.#s,n),r.run(a=>{a.get(this.source.enabled)&&(n.connect(i.context.destination),a.cleanup(()=>n.disconnect()))})}),this.#e.run(r=>{const i=r.get(this.#s);if(!i)return;r.cleanup(()=>i.gain.cancelScheduledValues(i.context.currentTime));const n=r.get(this.volume);n<Lr?(i.gain.exponentialRampToValueAtTime(Lr,i.context.currentTime+ls),i.gain.setValueAtTime(0,i.context.currentTime+ls+.01)):i.gain.exponentialRampToValueAtTime(n,i.context.currentTime+ls)})}close(){this.#e.close()}}class Ql{element;paused;#e;#t=new f(void 0);mediaSource=this.#t;#s=new C;constructor(e,s){this.element=f.from(s?.element),this.paused=f.from(s?.paused??!1),this.#e=e,this.#s.run(this.#r.bind(this)),this.#s.run(this.#i.bind(this)),this.#s.run(this.#n.bind(this)),this.#s.run(this.#a.bind(this)),this.#s.run(this.#o.bind(this))}#r(e){const s=e.get(this.element);if(!s)return;const r=new MediaSource;s.src=URL.createObjectURL(r),e.cleanup(()=>URL.revokeObjectURL(s.src)),e.event(r,"sourceopen",()=>{e.set(this.#t,r)},{once:!0}),e.event(r,"error",i=>{console.error("[MSE] MediaSource error event:",i)})}#i(e){const s=e.get(this.element);if(!s||e.get(this.paused))return;const r=g.toSecond(e.get(this.#e.latency));e.interval(()=>{const i=s.buffered;if(i.length===0)return;const n=i.end(i.length-1)-r,a=n-s.currentTime;(a>.1||a<-.1)&&(console.warn("seeking",a>0?"forward":"backward",Math.abs(a).toFixed(3),"seconds"),s.currentTime=n)},100)}#n(e){const s=e.get(this.element);if(!s)return;const r=e.get(this.mediaSource);r&&e.interval(async()=>{for(const i of r.sourceBuffers){for(;i.updating;)await new Promise(n=>i.addEventListener("updateend",n,{once:!0}));s.currentTime>10&&i.remove(0,s.currentTime-10)}},1e3)}#a(e){const s=e.get(this.element);if(!s)return;const r=e.get(this.paused);r&&!s.paused?s.pause():!r&&s.paused&&s.play().catch(i=>{console.error("[MSE] MediaElement play error:",i)})}#o(e){const s=e.get(this.element);if(!s||e.get(this.paused))return;const r=e.get(this.#e.reference);if(r===void 0)return;const i=e.get(this.#e.latency),n=g.sub(g.sub(g.now(),r),i);s.currentTime=g.toSecond(n)}close(){this.#s.close()}}class an{#e=new f(void 0);reference=this.#e;jitter;audio;video;#t=new f(g.zero);latency=this.#t;#s;#r;signals=new C;constructor(e){this.jitter=f.from(e?.jitter??100),this.audio=f.from(e?.audio),this.video=f.from(e?.video),this.#s=new Promise(s=>{this.#r=s}),this.signals.run(this.#i.bind(this))}#i(e){const s=e.get(this.jitter),r=e.get(this.video)??g.zero,i=e.get(this.audio)??g.zero,n=g.add(g.max(r,i),s);this.#t.set(n),this.#r(),this.#s=new Promise(a=>{this.#r=a})}received(e){const s=g.sub(g.now(),e),r=this.#e.peek();r!==void 0&&s>=r||(this.#e.set(s),this.#r(),this.#s=new Promise(i=>{this.#r=i}))}async wait(e){if(this.#e.peek()===void 0)throw new Error("reference not set; call update() first");for(;;){const s=g.now(),r=g.sub(s,e),i=this.#e.peek();if(i===void 0)return;const n=g.add(g.sub(i,r),this.#t.peek());if(n<=0)return;const a=new Promise(o=>setTimeout(o,n)).then(()=>!0);if(await Promise.race([this.#s,a]))return}}close(){this.signals.close()}}const eh=500,th=100;class sh{enabled;source;#e=new f(void 0);#t=new f(void 0);frame=this.#t;#s=new f(void 0);timestamp=this.#s;#r=new f(void 0);display=this.#r;#i=new f(!1);stalled=this.#i;#n=new f(void 0);stats=this.#n;#a=new f([]);buffered=this.#a;#o=new C;constructor(e,s){this.enabled=f.from(s?.enabled??!1),this.source=e,this.source.supported.set(nh),this.#o.run(this.#c.bind(this)),this.#o.run(this.#d.bind(this)),this.#o.run(this.#l.bind(this)),this.#o.run(this.#u.bind(this))}#c(e){const s=e.getAll([this.enabled,this.source.broadcast,this.source.track,this.source.config]);if(!s){this.#e.set(void 0);return}const[r,i,n,a]=s,o=e.get(i.active);if(!o)return;let c=new rh({source:this.source,broadcast:o,track:n,config:a,stats:this.#n});e.cleanup(()=>c?.close()),e.run(u=>{if(!c)return;const d=u.get(this.#e);if(d){const h=u.get(c.timestamp),p=u.get(d.timestamp);if(!h||p&&p>h+th)return}this.#e.set(c),c=void 0,u.close()})}#d(e){const s=e.get(this.#e);if(!s){this.#a.set([]);return}e.cleanup(()=>s.close()),e.run(r=>{const i=r.get(s.frame);this.#t.update(n=>(n?.close(),i?.clone()))}),e.proxy(this.#s,s.timestamp),e.proxy(this.#a,s.buffered)}#l(e){const s=e.get(this.source.catalog);if(!s)return;const r=s.display;if(r){e.set(this.#r,{width:r.width,height:r.height});return}const i=e.get(this.frame);i&&e.set(this.#r,{width:i.displayWidth,height:i.displayHeight})}#u(e){if(e.get(this.enabled)){if(!e.get(this.frame)){this.#i.set(!0);return}this.#i.set(!1),e.timer(()=>{this.#i.set(!0)},eh)}}close(){this.#t.update(e=>{e?.close()}),this.#o.close()}}class rh{source;broadcast;track;config;stats;timestamp=new f(void 0);frame=new f(void 0);buffered=new f([]);#e=new f([]);signals=new C;constructor(e){const{codedWidth:s,codedHeight:r,...i}=e.config;this.source=e.source,this.broadcast=e.broadcast,this.track=e.track,this.config=i,this.stats=e.stats,this.signals.run(this.#t.bind(this))}#t(e){const s=this.broadcast.subscribe(this.track,pt.video);e.cleanup(()=>s.close());const r=new VideoDecoder({output:async i=>{try{const n=g.fromMicro(i.timestamp);if(n<(this.timestamp.peek()??0))return;this.frame.peek()===void 0&&this.frame.set(i.clone());const a=this.source.sync.wait(n).then(()=>!0);if(!await Promise.race([a,e.cancel])||n<(this.timestamp.peek()??0))return;this.timestamp.set(n),this.#n(n),this.frame.update(o=>(o?.close(),i.clone()))}finally{i.close()}},error:i=>{console.error(i),e.close()}});e.cleanup(()=>r.close()),this.config.container.kind==="cmaf"?this.#r(e,s,r):this.#s(e,s,r)}#s(e,s,r){const i=new bt(s,{latency:this.source.sync.latency});e.cleanup(()=>i.close()),e.run(a=>{const o=a.get(i.buffered),c=a.get(this.#e);this.buffered.update(()=>ih(o,c))}),r.configure({...this.config,description:this.config.description?ke(this.config.description):void 0,optimizeForLatency:this.config.optimizeForLatency??!0,flip:!1});let n;e.spawn(async()=>{for(;;){const a=await Promise.race([i.next(),e.cancel]);if(!a)break;const{frame:o,group:c}=a;if(!o){n&&(n.final=!0);continue}this.source.sync.received(g.fromMicro(o.timestamp));const u=new EncodedVideoChunk({type:o.keyframe?"key":"delta",data:o.data,timestamp:o.timestamp});if(this.stats.update(d=>({frameCount:(d?.frameCount??0)+1,bytesReceived:(d?.bytesReceived??0)+o.data.byteLength})),n?.group===c||n?.final&&n.group+1===c){const d=g.fromMicro(n.timestamp),h=g.fromMicro(o.timestamp);this.#i(d,h)}n={timestamp:o.timestamp,group:c,final:!1},r.decode(u)}})}#r(e,s,r){if(this.config.container.kind!=="cmaf")return;const{timescale:i}=this.config.container,n=this.config.description?ke(this.config.description):void 0;r.configure({codec:this.config.codec,description:n,optimizeForLatency:this.config.optimizeForLatency??!0,flip:!1}),e.run(a=>{const o=a.get(this.#e);this.buffered.update(()=>o)}),e.spawn(async()=>{for(;;){const a=await Promise.race([s.nextGroup(),e.cancel]);if(!a)break;e.spawn(async()=>{let o;try{for(;;){const c=await Promise.race([a.readFrame(),e.cancel]);if(!c)break;const u=en(c,i);for(const d of u){const h=new EncodedVideoChunk({type:d.keyframe?"key":"delta",data:d.data,timestamp:d.timestamp});if(this.source.sync.received(g.fromMicro(d.timestamp)),this.stats.update(p=>({frameCount:(p?.frameCount??0)+1,bytesReceived:(p?.bytesReceived??0)+d.data.byteLength})),o!==void 0){const p=g.fromMicro(o),w=g.fromMicro(d.timestamp);this.#i(p,w)}o=d.timestamp,r.decode(h)}}}finally{a.close()}})}})}#i(e,s){e>s||this.#e.mutate(r=>{for(const i of r)if(i.start<=s&&i.end>=e){i.start=g.min(i.start,e),i.end=g.max(i.end,s);return}r.push({start:e,end:s}),r.sort((i,n)=>i.start-n.start)})}#n(e){this.#e.mutate(s=>{for(;s.length>0;){if(s[0].end>=e){s[0].start=g.max(s[0].start,e);break}s.shift()}})}close(){this.signals.close(),this.frame.update(e=>{e?.close()})}}function ih(t,e){if(t.length===0)return e;if(e.length===0)return t;const s=[],r=[...t,...e].sort((i,n)=>i.start-n.start);for(const i of r){const n=s.at(-1);n&&n.end>=i.start?n.end=g.max(n.end,i.end):s.push({...i})}return s}async function nh(t){const e=t.description?ke(t.description):void 0,{supported:s}=await VideoDecoder.isConfigSupported({codec:t.codec,description:e,optimizeForLatency:t.optimizeForLatency??!0});return s??!1}let ah=class{muxer;source;#e=new f(void 0);stats=this.#e;#t=new f([]);buffered=this.#t;#s=new f(!1);stalled=this.#s;#r=new f(g.zero);timestamp=this.#r;signals=new C;constructor(t,e){this.muxer=t,this.source=e,this.source.supported.set(oh),this.signals.run(this.#i.bind(this)),this.signals.run(this.#c.bind(this)),this.signals.run(this.#d.bind(this))}#i(t){const e=t.get(this.muxer.element);if(!e)return;const s=t.get(this.muxer.mediaSource);if(!s)return;const r=t.get(this.source.broadcast);if(!r)return;const i=t.get(r.active);if(!i)return;const n=t.get(this.source.track);if(!n)return;const a=t.get(this.source.config);if(!a)return;const o=`video/mp4; codecs="${a.codec}"`,c=s.addSourceBuffer(o);t.cleanup(()=>{s.removeSourceBuffer(c),c.abort()}),t.event(c,"error",u=>{console.error("[MSE] SourceBuffer error:",u)}),t.event(c,"updateend",()=>{this.#t.set(on(c.buffered))}),a.container.kind==="cmaf"?this.#a(t,i,n,a,c,e):this.#o(t,i,n,a,c,e)}async#n(t,e){for(;t.updating;)await new Promise(s=>t.addEventListener("updateend",s,{once:!0}));for(t.appendBuffer(e);t.updating;)await new Promise(s=>t.addEventListener("updateend",s,{once:!0}))}#a(t,e,s,r,i,n){if(r.container.kind!=="cmaf")throw new Error("unreachable");const a=e.subscribe(s,pt.video);t.cleanup(()=>a.close());const o=r.container.timescale;t.spawn(async()=>{const c=Dr(r);for(await this.#n(i,c);;){const u=await a.readFrame();if(!u)return;const d=Qi(u,o);this.source.sync.received(g.fromMicro(d)),await this.#n(i,u),n.buffered.length>0&&n.currentTime<n.buffered.start(0)&&(n.currentTime=n.buffered.start(0))}})}#o(t,e,s,r,i,n){const a=e.subscribe(s,pt.video);t.cleanup(()=>a.close());const o=new bt(a,{latency:this.source.sync.latency});t.cleanup(()=>o.close()),t.spawn(async()=>{const c=Dr(r);await this.#n(i,c);let u=1,d,h;for(;;){const p=await o.next();if(!p)return;if(p.frame){h=p.frame,this.source.sync.received(g.fromMicro(h.timestamp));break}}for(;;){const p=await o.next();if(p&&!p.frame)continue;const w=p?.frame;w&&(d=Rs.sub(w.timestamp,h.timestamp),this.source.sync.received(g.fromMicro(w.timestamp)));const y=nn({data:h.data,timestamp:h.timestamp,duration:d??0,keyframe:h.keyframe,sequence:u++});if(await this.#n(i,y),n.buffered.length>0&&n.currentTime<n.buffered.start(0)&&(n.currentTime=n.buffered.start(0)),!w)return;h=w}})}#c(t){const e=t.get(this.muxer.element);if(!e)return;const s=()=>{this.#s.set(e.readyState<=HTMLMediaElement.HAVE_CURRENT_DATA)};s(),t.event(e,"waiting",s),t.event(e,"playing",s),t.event(e,"seeking",s)}#d(t){const e=t.get(this.muxer.element);if(e)if("requestVideoFrameCallback"in e){const s=e;let r;const i=()=>{const n=g.fromSecond(s.currentTime);this.#r.set(n),r=s.requestVideoFrameCallback(i)};r=s.requestVideoFrameCallback(i),t.cleanup(()=>s.cancelVideoFrameCallback(r))}else t.event(e,"timeupdate",()=>{const s=g.fromSecond(e.currentTime);this.#r.set(s)})}close(){this.source.close(),this.signals.close()}};async function oh(t){return MediaSource.isTypeSupported(`video/mp4; codecs="${t.codec}"`)}class ch{decoder;canvas;paused;#e;#t=new f(void 0);#s=new C;constructor(e,s){this.decoder=e,this.canvas=f.from(s?.canvas),this.paused=f.from(s?.paused??!1),this.#s.run(r=>{const i=r.get(this.canvas);this.#t.set(i?.getContext("2d")??void 0)}),this.#s.run(this.#i.bind(this)),this.#s.run(this.#n.bind(this)),this.#s.run(this.#r.bind(this))}#r(e){const s=e.getAll([this.canvas,this.decoder.display]);if(!s)return;const[r,i]=s;(r.width!==i.width||r.height!==i.height)&&(r.width=i.width,r.height=i.height)}#i(e){const s=e.get(this.canvas);if(!s||e.get(this.paused))return;let r=!1;const i=new IntersectionObserver(a=>{for(const o of a)r=o.isIntersecting,this.decoder.enabled.set(r&&!document.hidden)},{threshold:.01}),n=()=>{this.decoder.enabled.set(r&&!document.hidden)};document.addEventListener("visibilitychange",n),e.cleanup(()=>document.removeEventListener("visibilitychange",n)),e.cleanup(()=>this.decoder.enabled.set(!1)),i.observe(s),e.cleanup(()=>i.disconnect())}#n(e){const s=e.get(this.#t);if(!s)return;let r;e.get(this.paused)?r=this.#e?.clone():(r=e.get(this.decoder.frame),this.#e?.close(),this.#e=r?.clone());let i=requestAnimationFrame(()=>{this.#a(s,r),i=void 0});e.cleanup(()=>{r?.close(),i&&cancelAnimationFrame(i)})}#a(e,s){if(!s){e.fillStyle="#000",e.fillRect(0,0,e.canvas.width,e.canvas.height);return}e.save(),e.fillStyle="#000",e.fillRect(0,0,e.canvas.width,e.canvas.height),this.decoder.source.catalog.peek()?.flip&&(e.scale(-1,1),e.translate(-e.canvas.width,0)),e.drawImage(s,0,0,e.canvas.width,e.canvas.height),e.restore()}close(){this.#e?.close(),this.#e=void 0,this.#s.close()}}function uh(t){return e=>{const s=[],r=[];for(const[i,n]of e)if(n.codedWidth&&n.codedHeight){const a=n.codedWidth*n.codedHeight;a<=t?s.push({name:i,size:a}):r.push({name:i,size:a})}return s.sort((i,n)=>n.size-i.size),s.length>0?s.map(i=>i.name):r.length>0?(r.sort((i,n)=>i.size-n.size),[r[0].name]):e.map(([i])=>i)}}function dh(t){return e=>{const s=[],r=[];for(const[i,n]of e)n.bitrate!=null&&n.bitrate<=t?s.push({name:i,bitrate:n.bitrate}):n.bitrate!=null&&r.push({name:i,bitrate:n.bitrate});return s.sort((i,n)=>n.bitrate-i.bitrate),s.length>0?s.map(i=>i.name):r.length>0?(r.sort((i,n)=>i.bitrate-n.bitrate),[r[0].name]):e.map(([i])=>i)}}function lh(t){let e=t[0];for(const s of t){const[,r]=s,[,i]=e,n=(r.codedWidth??0)*(r.codedHeight??0),a=(i.codedWidth??0)*(i.codedHeight??0);if(n!==a){n>a&&(e=s);continue}(r.bitrate??0)>(i.bitrate??0)&&(e=s)}return e[0]}let hh=class{broadcast;target;#e=new f(void 0);catalog=this.#e;#t=new f({});available=this.#t;#s=new f(void 0);track=this.#s;#r=new f(void 0);config=this.#r;sync;supported;#i=new C;constructor(t,e){this.broadcast=f.from(e?.broadcast),this.target=f.from(e?.target),this.sync=t,this.supported=f.from(e?.supported),this.#i.run(this.#n.bind(this)),this.#i.run(this.#a.bind(this)),this.#i.run(this.#o.bind(this))}#n(t){const e=t.get(this.broadcast);if(!e)return;const s=t.get(e.catalog)?.video;s&&t.set(this.#e,s)}#a(t){const e=t.get(this.supported);if(!e)return;const s=t.get(this.#e)?.renditions??{};t.spawn(async()=>{const r={};for(const[i,n]of Object.entries(s))await e(n)&&(r[i]=n);Object.keys(r).length===0&&Object.keys(s).length>0&&console.warn("[Source] No supported video renditions found:",s),this.#t.set(r)})}#o(t){const e=t.get(this.#t);if(Object.keys(e).length===0)return;const s=t.get(this.target),r=s?.name,i=r&&r in e?r:this.#c(e,s);if(!i)return;const n=e[i];t.set(this.#s,i),t.set(this.#r,n),t.set(this.sync.video,n.jitter)}#c(t,e){const s=Object.entries(t);if(s.length===0)return;if(s.length===1)return s[0][0];const r=[];if(e?.pixels!=null&&r.push(uh(e.pixels)),e?.bitrate!=null&&r.push(dh(e.bitrate)),r.length===0)return lh(s);const i=r.map(a=>a(s)),n=i.map(a=>new Set(a));for(const a of i[0])if(n.every(o=>o.has(a)))return a;console.warn("conflicting rendition filters, no rendition satisfies all criteria")}close(){this.#i.close()}};function on(t){const e=[];for(let s=0;s<t.length;s++){const r=g.fromSecond(t.start(s)),i=g.fromSecond(t.end(s));e.push({start:r,end:i})}return e}class fh{source;stats=new f(void 0);stalled=new f(!1);buffered=new f([]);timestamp=new f(g.zero);constructor(e){this.source=e}}class ph{source;volume=new f(.5);muted=new f(!1);stats=new f(void 0);buffered=new f([]);constructor(e){this.source=e}}class wh{element=new f(void 0);broadcast;jitter;paused;video;#e;audio;#t;#s;signals=new C;constructor(e){this.element=f.from(e?.element),this.broadcast=f.from(e?.broadcast),this.jitter=f.from(e?.jitter??100),this.#s=new an({jitter:this.jitter}),this.#e=new hh(this.#s,{broadcast:this.broadcast}),this.#t=new gh(this.#s,{broadcast:this.broadcast}),this.video=new fh(this.#e),this.audio=new ph(this.#t),this.paused=f.from(e?.paused??!1),this.signals.run(this.#r.bind(this))}#r(e){const s=e.get(this.element);s&&(s instanceof HTMLCanvasElement?this.#i(e,s):s instanceof HTMLVideoElement&&this.#n(e,s))}#i(e,s){const r=new sh(this.#e),i=new Jl(this.#t),n=new Yl(i,{volume:this.audio.volume,muted:this.audio.muted,paused:this.paused}),a=new ch(r,{canvas:s,paused:this.paused});e.cleanup(()=>{r.close(),i.close(),n.close(),a.close()}),e.proxy(this.video.stats,r.stats),e.proxy(this.video.buffered,r.buffered),e.proxy(this.video.stalled,r.stalled),e.proxy(this.video.timestamp,r.timestamp),e.proxy(this.audio.stats,i.stats),e.proxy(this.audio.buffered,i.buffered)}#n(e,s){const r=new Ql(this.#s,{paused:this.paused,element:s}),i=new ah(r,this.#e),n=new mh(r,this.#t,{volume:this.audio.volume,muted:this.audio.muted});e.cleanup(()=>{i.close(),n.close(),r.close()}),e.proxy(this.video.stats,i.stats),e.proxy(this.video.buffered,i.buffered),e.proxy(this.video.stalled,i.stalled),e.proxy(this.video.timestamp,i.timestamp),e.proxy(this.audio.stats,n.stats),e.proxy(this.audio.buffered,n.buffered)}close(){this.signals.close()}}class mh{muxer;source;volume;muted;#e=new f(void 0);stats=this.#e;#t=new f([]);buffered=this.#t;#s=new C;constructor(e,s,r){this.muxer=e,this.source=s,this.source.supported.set(bh),this.volume=f.from(r?.volume??.5),this.muted=f.from(r?.muted??!1),this.#s.run(this.#r.bind(this)),this.#s.run(this.#o.bind(this))}#r(e){const s=e.get(this.muxer.element);if(!s)return;const r=e.get(this.muxer.mediaSource);if(!r)return;const i=e.get(this.source.broadcast);if(!i)return;const n=e.get(i.active);if(!n)return;const a=e.get(this.source.track);if(!a)return;const o=e.get(this.source.config);if(!o)return;const c=`audio/mp4; codecs="${o.codec}"`,u=r.addSourceBuffer(c);e.cleanup(()=>{r.removeSourceBuffer(u),u.abort()}),e.event(u,"error",h=>{console.error("[MSE] SourceBuffer error:",h)}),e.event(u,"updateend",()=>{this.#t.set(on(u.buffered))});const d=n.subscribe(a,pt.audio);e.cleanup(()=>d.close()),o.container.kind==="cmaf"?this.#n(e,d,o,u,s):this.#a(e,d,o,u,s)}async#i(e,s){for(;e.updating;)await new Promise(r=>e.addEventListener("updateend",r,{once:!0}));for(e.appendBuffer(s);e.updating;)await new Promise(r=>e.addEventListener("updateend",r,{once:!0}))}#n(e,s,r,i,n){if(r.container.kind!=="cmaf")throw new Error("unreachable");const a=r.container.timescale;e.spawn(async()=>{const o=Fr(r);for(await this.#i(i,o);;){const c=await s.readFrame();if(!c)return;const u=Qi(c,a);this.source.sync.received(g.fromMicro(u)),await this.#i(i,c),n.buffered.length>0&&n.currentTime<n.buffered.start(0)&&(n.currentTime=n.buffered.start(0))}})}#a(e,s,r,i,n){const a=new bt(s,{latency:this.source.sync.latency});e.cleanup(()=>a.close()),e.spawn(async()=>{const o=Fr(r);await this.#i(i,o);let c=1,u,d;for(;;){const h=await a.next();if(!h)return;if(h.frame){d=h.frame,this.source.sync.received(g.fromMicro(d.timestamp));break}}for(;;){const h=await a.next();if(h&&!h.frame)continue;const p=h?.frame;p&&(u=Rs.sub(p.timestamp,d.timestamp),this.source.sync.received(g.fromMicro(p.timestamp)));const w=nn({data:d.data,timestamp:d.timestamp,duration:u??0,keyframe:d.keyframe,sequence:c++});if(await this.#i(i,w),n.buffered.length>0&&n.currentTime<n.buffered.start(0)&&(n.currentTime=n.buffered.start(0)),!p)return;d=p}})}#o(e){const s=e.get(this.muxer.element);if(!s)return;const r=e.get(this.volume),i=e.get(this.muted);i&&!s.muted?s.muted=!0:!i&&s.muted&&(s.muted=!1),r!==s.volume&&(s.volume=r),e.event(s,"volumechange",()=>{this.volume.set(s.volume)})}close(){this.#s.close()}}async function bh(t){return MediaSource.isTypeSupported(`audio/mp4; codecs="${t.codec}"`)}class gh{broadcast;target;#e=new f(void 0);catalog=this.#e;#t=new f({});available=this.#t;#s=new f(void 0);track=this.#s;#r=new f(void 0);config=this.#r;supported;sync;#i=new C;constructor(e,s){this.sync=e,this.broadcast=f.from(s?.broadcast),this.target=f.from(s?.target),this.supported=f.from(s?.supported),this.#i.run(this.#n.bind(this)),this.#i.run(this.#a.bind(this)),this.#i.run(this.#o.bind(this))}#n(e){const s=e.get(this.broadcast);if(!s)return;const r=e.get(s.catalog)?.audio;r&&e.set(this.#e,r)}#a(e){const s=e.get(this.#e)?.renditions??{},r=e.get(this.supported);r&&e.spawn(async()=>{const i={};for(const[n,a]of Object.entries(s))await r(a)&&(i[n]=a);Object.keys(i).length===0&&Object.keys(s).length>0&&console.warn("no supported audio renditions found:",s),this.#t.set(i)})}#o(e){const s=e.get(this.#t);if(Object.keys(s).length===0)return;const r=e.get(this.target);let i;if(r?.name&&r.name in s)i={track:r.name,config:s[r.name]};else if(i=this.#c(s),!i)return;e.set(this.#s,i.track),e.set(this.#r,i.config),e.set(this.sync.audio,i.config.jitter)}#c(e){const s=Object.entries(e);if(s.length!==0){for(const[r,i]of s)if(i.container.kind==="legacy")return{track:r,config:i};for(const[r,i]of s)if(i.container.kind==="cmaf")return{track:r,config:i}}}close(){this.#i.close()}}class yh{connection;enabled;name;status=new f("offline");reload;#e=new f(void 0);active=this.#e;#t=new f(void 0);catalog=this.#t;#s=new f(!1);signals=new C;constructor(e){this.connection=f.from(e?.connection),this.name=f.from(e?.name??wt()),this.enabled=f.from(e?.enabled??!1),this.reload=f.from(e?.reload??!1),this.signals.run(this.#r.bind(this)),this.signals.run(this.#i.bind(this)),this.signals.run(this.#n.bind(this))}#r(e){if(!e.get(this.enabled))return;if(!e.get(this.reload)){e.set(this.#s,!0,!1);return}const s=e.get(this.connection);if(!s)return;const r=e.get(this.name),i=s.announced(r);e.cleanup(()=>i.close()),e.spawn(async()=>{for(;;){const n=await i.next();if(!n)break;if(n.path!==r){console.warn("ignoring announce",n.path);continue}e.set(this.#s,n.active,!1)}})}#i(e){const s=e.getAll([this.enabled,this.#s,this.connection]);if(!s)return;const[r,i,n]=s,a=e.get(this.name),o=n.consume(a);e.cleanup(()=>o.close()),e.set(this.#e,o)}#n(e){const s=e.getAll([this.enabled,this.active]);if(!s)return;const[r,i]=s;this.status.set("loading");const n=i.subscribe("catalog.json",pt.catalog);e.cleanup(()=>n.close()),e.spawn(this.#a.bind(this,n))}async#a(e){try{for(;;){const s=await tl(e);if(!s)break;console.debug("received catalog",this.name.peek(),s),this.#t.set(s),this.status.set("live")}}catch(s){console.warn("error fetching catalog",this.name.peek(),s)}finally{this.#t.set(void 0),this.status.set("offline")}}close(){this.signals.close()}}const vh=["url","name","paused","volume","muted","reload","jitter"],_h=new FinalizationRegistry(t=>t.close());class kh extends HTMLElement{static observedAttributes=vh;connection;broadcast;sync=new an;backend;#e=new f(!1);signals=new C;constructor(){super(),_h.register(this,this.signals),this.connection=new ta({enabled:this.#e}),this.signals.cleanup(()=>this.connection.close()),this.broadcast=new yh({connection:this.connection.established,enabled:this.#e}),this.signals.cleanup(()=>this.broadcast.close()),this.backend=new wh({broadcast:this.broadcast}),this.signals.cleanup(()=>this.backend.close());const e=()=>{const r=this.querySelector("canvas"),i=this.querySelector("video");if(r&&i)throw new Error("Cannot have both canvas and video elements");this.backend.element.set(r??i)},s=new MutationObserver(e);s.observe(this,{childList:!0,subtree:!0}),this.signals.cleanup(()=>s.disconnect()),e(),this.signals.run(r=>{const i=r.get(this.connection.url);i?this.setAttribute("url",i.toString()):this.removeAttribute("url")}),this.signals.run(r=>{const i=r.get(this.broadcast.name);this.setAttribute("name",i.toString())}),this.signals.run(r=>{r.get(this.backend.audio.muted)?this.setAttribute("muted",""):this.removeAttribute("muted")}),this.signals.run(r=>{r.get(this.backend.paused)?this.setAttribute("paused","true"):this.removeAttribute("paused")}),this.signals.run(r=>{const i=r.get(this.backend.audio.volume);this.setAttribute("volume",i.toString())}),this.signals.run(r=>{const i=Math.floor(r.get(this.backend.jitter));this.setAttribute("jitter",i.toString())})}connectedCallback(){this.#e.set(!0),this.style.display="block",this.style.position="relative"}disconnectedCallback(){this.#e.set(!1)}attributeChangedCallback(e,s,r){if(s!==r)if(e==="url")this.connection.url.set(r?new URL(r):void 0);else if(e==="name")this.broadcast.name.set(Ze(r??""));else if(e==="paused")this.backend.paused.set(r!==null);else if(e==="volume"){const i=r?Number.parseFloat(r):.5;this.backend.audio.volume.set(i)}else if(e==="muted")this.backend.audio.muted.set(r!==null);else if(e==="reload")this.broadcast.reload.set(r!==null);else if(e==="jitter")this.backend.jitter.set(r?Number.parseFloat(r):100);else{const i=e;throw new Error(`Invalid attribute: ${i}`)}}get url(){return this.connection.url.peek()}set url(e){this.connection.url.set(e?new URL(e):void 0)}get name(){return this.broadcast.name.peek()}set name(e){this.broadcast.name.set(Ze(e))}get paused(){return this.backend.paused.peek()}set paused(e){this.backend.paused.set(e)}get volume(){return this.backend.audio.volume.peek()}set volume(e){this.backend.audio.volume.set(e)}get muted(){return this.backend.audio.muted.peek()}set muted(e){this.backend.audio.muted.set(e)}get reload(){return this.broadcast.reload.peek()}set reload(e){this.broadcast.reload.set(e)}get jitter(){return this.backend.jitter.peek()}set jitter(e){this.backend.jitter.set(e)}}customElements.define("moq-watch",kh);const Eh=navigator.userAgent.toLowerCase().includes("firefox"),ks={aac:"mp4a.40.2",opus:"opus",av1:"av01.0.08M.08",h264:"avc1.640028",h265:"hev1.1.6.L93.B0",vp9:"vp09.00.10.08",vp8:"vp8"};async function Br(t){return globalThis.AudioDecoder?(await AudioDecoder.isConfigSupported({codec:ks[t],numberOfChannels:2,sampleRate:48e3})).supported===!0:!1}async function gt(t){const e=await VideoDecoder.isConfigSupported({codec:ks[t],hardwareAcceleration:"prefer-software"}),s=await VideoDecoder.isConfigSupported({codec:ks[t],hardwareAcceleration:"prefer-hardware"});return{hardware:Eh||s.config?.hardwareAcceleration!=="prefer-hardware"?void 0:s.supported===!0,software:e.supported===!0}}async function Ih(){return{webtransport:typeof WebTransport<"u"?"full":"partial",audio:{decoding:{aac:await Br("aac"),opus:await Br("opus")?"full":"partial"},render:typeof AudioContext<"u"&&typeof AudioBufferSourceNode<"u"},video:{decoding:typeof VideoDecoder<"u"?{h264:await gt("h264"),h265:await gt("h265"),vp8:await gt("vp8"),vp9:await gt("vp9"),av1:await gt("av1")}:void 0,render:typeof OffscreenCanvas<"u"&&typeof CanvasRenderingContext2D<"u"}}}function ie(t,e,...s){const r=document.createElement(t);if(!e)return r;const{style:i,classList:n,dataset:a,attributes:o,...c}=e;return i&&Object.assign(r.style,i),n&&r.classList.add(...n),a&&Object.entries(a).forEach(([u,d])=>{r.dataset[u]=d}),o&&Object.entries(o).forEach(([u,d])=>{r.setAttribute(u,d)}),s&&s.forEach(u=>{typeof u=="string"?r.appendChild(document.createTextNode(u)):r.appendChild(u)}),Object.assign(r,c),r}const Vr=navigator.userAgent.toLowerCase().includes("firefox"),Sh=["show","details"];class Ah extends HTMLElement{#e=new f("warning");#t=new f(!1);#s=new f(void 0);#r=new f(!1);#i;static observedAttributes=Sh;constructor(){super(),Ih().then(e=>this.#s.set(e)).catch(e=>console.error("Failed to detect watch support:",e))}connectedCallback(){this.#i=new C,this.#i.run(this.#a.bind(this))}disconnectedCallback(){this.#i?.close(),this.#i=void 0}attributeChangedCallback(e,s,r){if(e==="show"){const i=r??"warning";if(i==="always"||i==="warning"||i==="error"||i==="never")this.show=i;else throw new Error(`Invalid show: ${i}`)}else if(e==="details")this.details=r!==null;else{const i=e;throw new Error(`Invalid attribute: ${i}`)}}get show(){return this.#e.peek()}set show(e){this.#e.set(e)}get details(){return this.#t.peek()}set details(e){this.#t.set(e)}#n(e){return e.webtransport==="none"||!e.audio.decoding||!e.video.decoding||!e.audio.render||!e.video.render||!Object.values(e.audio.decoding).some(s=>s===!0||s==="full"||s==="partial")||!Object.values(e.video.decoding).some(s=>s.software||s.hardware)?"none":!Object.values(e.audio.decoding).every(s=>s===!0||s==="full")||!Object.values(e.video.decoding).every(s=>s.software||s.hardware)?"partial":"full"}#a(e){const s=e.get(this.#s);if(!s||e.get(this.#r))return;const r=e.get(this.#e);if(r==="never")return;const i=this.#n(s);if(r==="warning"&&i==="full"||r==="error"&&i!=="none")return;const n=ie("div",{style:{margin:"0 auto",maxWidth:"28rem",padding:"1rem"}});this.appendChild(n),e.cleanup(()=>this.removeChild(n)),this.#o(n,i,e),e.get(this.#t)&&this.#c(n,s,e)}#o(e,s,r){const i=ie("div",{style:{display:"flex",flexDirection:"row",gap:"1rem",flexWrap:"wrap",justifyContent:"space-between",alignItems:"center"}}),n=ie("div",{style:{fontWeight:"bold"}});s==="full"?n.textContent="🟢 Full Browser Support":s==="partial"?n.textContent="🟡 Partial Browser Support":s==="none"&&(n.textContent="🔴 No Browser Support");const a=ie("button",{type:"button",style:{fontSize:"14px"}});r.event(a,"click",()=>{this.#t.update(c=>!c)}),r.run(c=>{a.textContent=c.get(this.#t)?"Details ➖":"Details ➕"});const o=ie("button",{type:"button",style:{fontSize:"14px"}},"Close ❌");r.event(o,"click",()=>{this.#r.set(!0)}),i.appendChild(n),i.appendChild(a),i.appendChild(o),e.appendChild(i),r.cleanup(()=>e.removeChild(i))}#c(e,s,r){const i=ie("div",{style:{display:"grid",gridTemplateColumns:"1fr 1fr 1fr",columnGap:"0.5rem",rowGap:"0.2rem",backgroundColor:"rgba(0, 0, 0, 0.6)",borderRadius:"0.5rem",padding:"1rem",fontSize:"0.875rem"}}),n=u=>u?"🟢 Yes":"🔴 No",a=u=>u?.hardware?"🟢 Hardware":u?.software?`🟡 Software${Vr?"*":""}`:"🔴 No",o=u=>u==="full"?"🟢 Full":u==="partial"?"🟡 Polyfill":"🔴 None",c=(u,d,h)=>{const p=ie("div",{style:{gridColumnStart:"1",fontWeight:"bold",textAlign:"right"}},u),w=ie("div",{style:{gridColumnStart:"2",textAlign:"center"}},d),y=ie("div",{style:{gridColumnStart:"3"}},h);i.appendChild(p),i.appendChild(w),i.appendChild(y)};if(c("WebTransport","",o(s.webtransport)),c("Rendering","Audio",n(s.audio.render)),c("","Video",n(s.video.render)),c("Decoding","Opus",o(s.audio.decoding.opus)),c("","AAC",n(s.audio.decoding.aac)),c("","AV1",a(s.video.decoding?.av1)),c("","H.265",a(s.video.decoding?.h265)),c("","H.264",a(s.video.decoding?.h264)),c("","VP9",a(s.video.decoding?.vp9)),c("","VP8",a(s.video.decoding?.vp8)),Vr){const u=ie("div",{style:{gridColumnStart:"1",gridColumnEnd:"4",textAlign:"center",fontSize:"0.875rem",fontStyle:"italic"}},"Hardware acceleration is ",ie("a",{href:"https://github.com/w3c/webcodecs/issues/896"},"undetectable")," on Firefox.");i.appendChild(u)}e.appendChild(i),r.cleanup(()=>e.removeChild(i))}}customElements.define("moq-watch-support",Ah);const W=document.getElementById("player"),Fs=document.getElementById("errors"),xh=document.getElementById("url-rows"),Th=document.getElementById("info-name"),jr=document.getElementById("info-url"),Gr=document.getElementById("info-mode"),St=document.getElementById("info-status"),Rh=document.querySelectorAll("input[name=mode]"),At=document.getElementById("volume-slider"),Es=document.getElementById("volume-icon"),Zr=document.getElementById("volume-label"),Jt=document.getElementById("jitter-slider"),Is=document.getElementById("jitter-label"),Dt=document.getElementById("dbg-fps"),Wr=document.getElementById("dbg-buf"),Hr=document.getElementById("dbg-stalled"),Ph=document.getElementById("dbg-latency"),Oh=document.getElementById("dbg-jitter"),Jr=document.getElementById("dbg-vbytes"),Nh=document.getElementById("dbg-codec");function Ge(t,e){return`<span class="badge badge-${e}">${t}</span>`}function cn(t){const e=document.createElement("div");e.className="banner banner-error",e.innerHTML=`⛔ ${t}`,Fs.appendChild(e)}const at=new URLSearchParams(window.location.search),Xt=at.get("name")??at.get("broadcast")??at.get("id")??null,Uh=at.get("url")??at.get("host")??null,zh=at.get("fallbackPlayer")==="true";Xt||(cn("No broadcast name provided. Add <code>?name=your-broadcast</code> (or <code>?broadcast=</code> / <code>?id=</code>) to the URL."),St.innerHTML=Ge("no stream name","red"));Th.textContent=Xt??"—";const Ms=typeof VideoDecoder<"u";if(!Ms){const t=document.createElement("div");t.className="banner banner-warn",t.innerHTML="⚠️ <strong>WebCodecs is not supported in this browser.</strong> Falling back to MSE player. Chrome 94+, Edge 94+, or Safari 17.4+ required for WebCodecs.",Fs.appendChild(t)}const hs=100,fs=200;function un(t){const e=t==="webcodecs"||t==="auto"&&Ms,s=W.querySelector("canvas, video");if(s&&s.remove(),e){const r=document.createElement("canvas");W.appendChild(r),W.setAttribute("jitter",hs),Jt.value=hs,Is.textContent=`${hs} ms`,Gr.innerHTML=Ge("WebCodecs (canvas)","blue")}else{const r=document.createElement("video");r.autoplay=!0,r.muted=!0,W.appendChild(r),W.setAttribute("jitter",fs),Jt.value=fs,Is.textContent=`${fs} ms`,Gr.innerHTML=Ge("MSE (video)","yellow")}}const dn=zh?"mse":"auto";un(dn);document.querySelector(`input[name=mode][value="${dn}"]`).checked=!0;Rh.forEach(t=>{t.addEventListener("change",()=>un(t.value))});function Ss(t){t===0?(W.setAttribute("muted",""),Es.textContent="🔇",Zr.textContent="muted"):(W.removeAttribute("muted"),W.setAttribute("volume",(t/100).toFixed(2)),Es.textContent=t<50?"🔉":"🔊",Zr.textContent=`${t}%`)}At.addEventListener("input",()=>Ss(Number(At.value)));Es.addEventListener("click",()=>{if(W.hasAttribute("muted")){const t=Number(At.value)||50;At.value=t,Ss(t)}else At.value=0,Ss(0)});Jt.addEventListener("input",()=>{const t=Number(Jt.value);Is.textContent=`${t} ms`,W.setAttribute("jitter",t)});let Xr=0;function qh(t){return t==null?"—":t<1024?`${t} B`:t<1024*1024?`${(t/1024).toFixed(1)} KB`:`${(t/(1024*1024)).toFixed(2)} MB`}setInterval(()=>{const t=W.backend;if(!t)return;const e=t.video.stats.peek();if(e){const o=e.frameCount-Xr;Xr=e.frameCount,Dt.textContent=`${o} fps`,Dt.className="dbg-value ok",Jr.textContent=qh(e.bytesReceived)}else Dt.textContent="N/A (MSE)",Dt.className="dbg-value",Jr.textContent="—";const s=t.video.buffered.peek();if(s&&s.length>0){const o=s[s.length-1],c=o.end-o.start;Wr.textContent=`${c.toFixed(0)} ms`}else Wr.textContent="empty";const r=t.video.stalled.peek();Hr.textContent=r?"yes":"no",Hr.className=`dbg-value ${r?"stalled":"ok"}`;const i=t.video.source.sync.latency.peek();Ph.textContent=i!=null?`${i.toFixed(0)} ms`:"—";const n=t.jitter.peek();Oh.textContent=n!=null?`${n} ms`:"—";const a=t.video.source.config.peek();Nh.textContent=a?.codec??"—"},1e3);if(!Ms){const t=document.querySelector("input[value=webcodecs]");t&&(t.disabled=!0,t.closest("label").style.opacity="0.4",t.closest("label").title="WebCodecs not supported in this browser")}function $h(t){if(t&&t.includes("://"))try{return new URL(t).hostname}catch{}return t?t.split(":")[0]:window.location.hostname||"localhost"}const Kr=$h(Uh),As=window.location.protocol,_t=[`${As}//${Kr}/moq`,`${As}//${Kr}:4443/moq`];if(As==="http:"){const t=window.location.href.replace(/^http:/,"https:"),e=document.createElement("div");e.className="banner banner-warn",e.innerHTML=`⚠️ <strong>Page loaded over HTTP.</strong> WebTransport requires HTTPS — playback may fail. <a href="${t}" style="color:inherit;font-weight:bold;">Switch to HTTPS →</a>`,Fs.appendChild(e)}_t.forEach((t,e)=>{const s=document.createElement("tr");s.id=`url-row-${e}`,s.innerHTML=`
    <td>${e+1}</td>
    <td>${t}</td>
    <td id="url-status-${e}"><span class="status-dot dot-pending"></span>pending</td>
  `,xh.appendChild(s)});function Ft(t,e,s){const r=document.getElementById(`url-status-${t}`),i={pending:"dot-pending",trying:"dot-trying",ok:"dot-ok",fail:"dot-fail"}[e];r.innerHTML=`<span class="status-dot ${i}"></span>${s}`;const n=document.getElementById(`url-row-${t}`);n.className=e==="ok"?"url-row active":"url-row"}async function Ch(t,e=4e3){const s=new AbortController,r=setTimeout(()=>s.abort(),e);try{return await fetch(t,{method:"HEAD",mode:"no-cors",signal:s.signal}),!0}catch(i){return i.name==="AbortError"?!1:!(i instanceof TypeError)}finally{clearTimeout(r)}}async function Dh(){for(let t=0;t<_t.length;t++){const e=_t[t];if(Ft(t,"trying","trying…"),St.innerHTML=Ge(`probing ${t+1}/${_t.length}`,"yellow"),await Ch(e)){Ft(t,"ok","✓ reachable");for(let r=t+1;r<_t.length;r++)Ft(r,"pending","skipped");return e}else Ft(t,"fail","✗ unreachable")}return null}(async()=>{const t=await Dh();if(!t){cn("Could not reach any relay URL. Check that the relay server is running and the host is correct."),jr.textContent="none",St.innerHTML=Ge("unreachable","red");return}jr.textContent=t,St.innerHTML=Ge("connecting…","yellow"),W.setAttribute("url",t),Xt&&(W.setAttribute("name",Xt),St.innerHTML=Ge("connected","green"))})();
