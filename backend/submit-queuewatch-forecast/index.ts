import { validate } from './validation.ts';
const reply = (status: number, body: unknown) => new Response(JSON.stringify(body), {status,headers:{'Content-Type':'application/json','Cache-Control':'no-store'}});
Deno.serve(async (req: Request) => {
  if (req.method!=='POST') return reply(405,{error:'post_required'});
  if (!req.headers.get('content-type')?.toLowerCase().startsWith('application/json')) return reply(415,{error:'json_required'});
  try {
    // Bound streamed bodies too, regardless of Content-Length.
    const reader=req.body?.getReader(); if(!reader) return reply(400,{error:'body_required'});
    let size=0; const chunks: Uint8Array[]=[];
    while(true) { const {done,value}=await reader.read(); if(done)break; size+=value.length;
      if(size>32768){await reader.cancel();return reply(413,{error:'body_too_large'});} chunks.push(value); }
    const bytes=new Uint8Array(size); let offset=0; for(const c of chunks){bytes.set(c,offset);offset+=c.length;}
    let event; try{event=JSON.parse(new TextDecoder().decode(bytes));}catch{return reply(400,{error:'invalid_json'});}
    const error=validate(event); if(error)return reply(400,{error});
    const key=Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
    const response=await fetch(Deno.env.get('SUPABASE_URL')+'/rest/v1/rpc/ingest_queuewatch_forecast',{
      method:'POST',headers:{apikey:key,Authorization:'Bearer '+key,'Content-Type':'application/json'},body:JSON.stringify({e:event})
    });
    if(!response.ok) return reply(503,{error:'storage_unavailable'});
    const result=await response.json(); return reply(result.status??200,result);
  }catch{return reply(503,{error:'temporarily_unavailable'});}
});
