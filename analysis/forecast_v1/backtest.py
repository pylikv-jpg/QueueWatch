"""Reproducible as-of replay. Private source rows never enter exported artifacts."""
import argparse, bisect, collections, csv, datetime as dt, json, math, statistics
from pathlib import Path

VERSION='v1.1'
MINUTE=60000
TYPES={1:'CAR',2:'TRUCK',3:'BUS',4:'MOTORCYCLE'}
def iso(ms):return dt.datetime.fromtimestamp(ms/1000,dt.timezone.utc).isoformat().replace('+00:00','Z')
def quantile(values,p):
    if not values:return 0.0
    a=sorted(values);i=(len(a)-1)*p;lo=int(i);hi=math.ceil(i)
    return a[lo]+(a[hi]-a[lo])*(i-lo)
def estimate(position,history,live,p80=0.0):
    if position<1:return None
    if position==1:return dict(eta=0.,low=0.,high=0.,speed=0.,confidence='LOW')
    if not history and not live:return None
    w=0 if not live else 1 if not history else .15 if live[1]<3 else .30 if live[1]<6 else .55
    speed=live[0] if not history else history[0] if not live else history[0]*(1-w)+live[0]*w
    eta=(position-1)*60/speed
    disagreement=history and live and abs(history[0]-live[0])/history[0]>.5
    confidence='LOW' if not p80 or disagreement else 'HIGH' if history and history[1]>=50 and live and live[1]>=6 else 'MEDIUM' if history and history[1]>=15 else 'LOW'
    radius=max(p80,max(30.,eta*.5) if confidence=='LOW' else 10.)
    return dict(eta=eta,low=max(0.,eta-radius),high=eta+radius,speed=speed,confidence=confidence)
def historical_at(rows, at):
    rates=[rate for end,rate in rows if end<at]
    if len(rates)<10:return None
    rate=statistics.median(rates)
    return (rate,len(rates)) if .25<=rate<=200 else None

def load(root,kind):
    unique={}; duplicates=0
    for p in sorted(root.glob('*/'+kind+'/*.jsonl')):
        for line in p.read_text().splitlines():
            if not line:continue
            row=json.loads(line);key=row['id']
            if key in unique:
                if unique[key]!=row:raise ValueError('Conflicting record ID')
                duplicates+=1
            unique[key]=row
    return sorted(unique.values(),key=lambda r:(r['timestamp'],r['id'])),duplicates

def band(p):return '1-5' if p<=5 else '6-20' if p<=20 else '21-50' if p<=50 else '51-100' if p<=100 else '101+'

def run(root,source_sha,out,asset):
    events,duplicates=load(root,'vehicle_events');samples,sample_duplicates=load(root,'queue_samples')
    cutoff=max(e['timestamp'] for e in events)
    samples=[s for s in samples if s['timestamp']<=cutoff]
    snapshots=collections.defaultdict(set);names={}
    for s in samples:snapshots[s['checkpoint_id']].add(s['timestamp']);names[s['checkpoint_id']]=s['checkpoint_name']
    for k in snapshots:snapshots[k]=sorted(snapshots[k])
    # One median movement per checkpoint/type/observation; not per vehicle.
    movements=collections.defaultdict(dict)
    visits=collections.defaultdict(list)
    for e in events:
        if e['vehicle_type'] not in TYPES:continue
        group=(e['checkpoint_id'],TYPES[e['vehicle_type']]);at=e['timestamp']
        visits[(group,e['registration_number'],e.get('registration_date'))].append(e)
        before,after=e['previous_position'],e['current_position']
        if e['event_type']=='MOVE' and e['previous_status']==2 and e['current_status']==2 and before and after and before>after:
            movements[(group,at)][e['registration_number']]=before-after
    batches=collections.defaultdict(dict)
    for (group,at),deltas in movements.items():
        advance=statistics.median(deltas.values())
        if 0<advance<=100:batches[group][at]=advance
    intervals={};blocks={};gaps=0;unmatched=0
    for group,advances in batches.items():
        stamps=snapshots.get(group[0],[]);seq=[]
        for before,at in zip(stamps,stamps[1:]):
            if 0<at-before<=10*MINUTE:seq.append((before,at,advances.get(at,0.)))
            else:gaps+=1
        unmatched+=len(set(advances)-set(stamps))
        intervals[group]=seq
        # Non-overlapping 60-minute blocks include stopped time. Only well-observed blocks.
        chunks=collections.defaultdict(list)
        for a,b,d in seq:
            if a//(60*MINUTE)==b//(60*MINUTE):chunks[b//(60*MINUTE)].append((a,b,d))
        cells=[]
        for chunk,rows in sorted(chunks.items()):
            elapsed=sum(b-a for a,b,_ in rows)
            if elapsed>=50*MINUTE:
                rate=sum(d for _,_,d in rows)*60*MINUTE/elapsed
                if 0<=rate<=200:cells.append((max(b for _,b,_ in rows),rate))
        blocks[group]=cells
    # Exact timestamp matching is mandatory; fail rather than quietly treating movement as zero.
    if unmatched:raise ValueError(f'{unmatched} movement batches have no corresponding observation timestamp')
    cases=[];completed=0;uncertain_calls=0
    for (group,_,_),rows in visits.items():
        seen_bands=set();observations=[];closed=False
        for e in rows:
            if closed:continue
            if e['current_status']==2 and e['current_position'] and e['current_position']>0:
                p=e['current_position'];b=band(p)
                if b not in seen_bands:seen_bands.add(b);observations.append((e['timestamp'],p,b))
            if e['previous_status']==2 and e['current_status']==3:
                closed=True;called=e['timestamp'];stamps=snapshots.get(group[0],[]);i=bisect.bisect_left(stamps,called)
                # Timestamp bracket from the actual observation stream, not last MOVE.
                if i==0 or i==len(stamps) or stamps[i]!=called or called-stamps[i-1]>2*MINUTE:
                    uncertain_calls+=1;continue
                completed+=1
                for at,p,b in observations:
                    if called>at:cases.append(dict(group=group,at=at,position=p,band=b,called=called,actual=(called-at)/MINUTE))
    ends={g:[r[1] for r in rows] for g,rows in intervals.items()}
    block_ends={g:[r[0] for r in rows] for g,rows in blocks.items()}
    def inputs(g,at):
        history=historical_at(blocks.get(g,[]),at)
        seq=intervals.get(g,[]);i=bisect.bisect_right(ends.get(g,[]),at)
        seq=seq[max(0,i-300):i];seq=[r for r in seq if r[0]>=at-60*MINUTE]
        if not seq or at-seq[-1][1]>2*MINUTE:return history,None,False
        # Reset after a missing interval, matching the Android estimator.
        for j in range(len(seq)-1,0,-1):
            if seq[j][0]!=seq[j-1][1]:seq=seq[j:];break
        covered=sum(b-a for a,b,_ in seq);n=sum(d>0 for _,_,d in seq)
        recent=[r for r in seq if r[0]>=at-30*MINUTE]
        stopped=sum(b-a for a,b,_ in recent)>=20*MINUTE and all(d==0 for _,_,d in recent)
        live=None
        if covered>=10*MINUTE and n:
            rate=sum(d for _,_,d in seq)*60*MINUTE/covered
            if .25<=rate<=200:live=(rate,n)
        return history,live,stopped
    predictions=[]
    for case in sorted(cases,key=lambda c:c['at']):
        history,live,stopped=inputs(case['group'],case['at'])
        result=None if stopped else estimate(case['position'],history,live)
        if result is not None:predictions.append(dict(case,history=history,live=live,result=result,error=abs(result['eta']-case['actual'])))
    # Only errors whose calls were already observed may calibrate a later interval.
    resolved=sorted(predictions,key=lambda c:c['called']);cursor=0;errors=collections.defaultdict(list)
    for pred in predictions:
        while cursor<len(resolved) and resolved[cursor]['called']<pred['at']:
            past=resolved[cursor];errors[past['group']].append(past['error']);cursor+=1
        known=errors[pred['group']];p80=quantile(known,.8) if len(known)>=30 else 0
        pred['result']=estimate(pred['position'],pred['history'],pred['live'],p80)
    split=min(e['timestamp'] for e in events)+(cutoff-min(e['timestamp'] for e in events))*2/3
    def metrics(rows):
        if not rows:return dict(predictions=0)
        return dict(predictions=len(rows),median_absolute_error_minutes=statistics.median(p['error'] for p in rows),mae_minutes=statistics.mean(p['error'] for p in rows),median_signed_error_minutes=statistics.median(p['result']['eta']-p['actual'] for p in rows),p80_absolute_error_minutes=quantile([p['error'] for p in rows],.8),interval_coverage=statistics.mean(p['result']['low']<=p['actual']<=p['result']['high'] for p in rows))
    group_metrics=[]
    for group in sorted(batches):
        group_metrics.append(dict(checkpoint_id=group[0],checkpoint_name=names.get(group[0]),vehicle_type=group[1],**metrics([p for p in predictions if p['group']==group and p['at']>=split])))
    all_errors=collections.defaultdict(list)
    for p in predictions:all_errors[p['group']].append(p['error'])
    entries=[]
    for group,rows in sorted(blocks.items()):
        if len(rows)<10:continue
        rate=statistics.median(r[1] for r in rows)
        if not .25<=rate<=200:continue
        err=all_errors[group]
        entries.append(dict(checkpoint_id=group[0],vehicle_type=group[1],hour_bucket_start=-1,hour_bucket_size=3,positions_per_hour=rate,sample_count=len(rows),absolute_error_p50_minutes=quantile(err,.5) if len(err)>=30 else 0,absolute_error_p80_minutes=quantile(err,.8) if len(err)>=30 else 0,calibration_samples=len(err)))
    artifact=dict(schema_version=1,algorithm_version=VERSION,generated_at=dt.datetime.now(dt.timezone.utc).isoformat(),data_cutoff=iso(cutoff),source=dict(repository='pylikv-jpg/QueueLoggerData',commit=source_sha,record_timestamp_based=True),entries=entries)
    report=dict(algorithm_version=VERSION,source_commit=source_sha,unique_events=len(events),duplicate_rows=duplicates,queue_samples=len(samples),event_time_start=iso(min(e['timestamp'] for e in events)),event_time_end=iso(cutoff),completed_sessions=completed,excluded_uncertain_calls=uncertain_calls,observation_gaps=gaps,independent_movement_batches=sum(map(len,batches.values())),hourly_blocks=sum(map(len,blocks.values())),candidate_predictions=len(cases),available_predictions=len(predictions),holdout_start=iso(split),overall=metrics(predictions),chronological_holdout=metrics([p for p in predictions if p['at']>=split]),holdout_by_checkpoint_type=group_metrics,baseline_entries=len(entries),limitations=['Only September 1-4 event data has been exported, despite September 24 upload filenames.','Backtest estimates call time; position 1 is only front-of-queue, not confirmation.','Intervals are calibrated only on calls preceding each prediction; sparse cells use a provisional interval.','Type-specific event replay uses the positive median movement; Android additionally rejects unsupported isolated moves.','Single archive and short observation period; field validation is required.'])
    out.mkdir(parents=True,exist_ok=True);(out/'backtest_v1.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    asset.parent.mkdir(parents=True,exist_ok=True);asset.write_text(json.dumps(artifact,ensure_ascii=False,indent=2)+'\n')
    with (out/'backtest_v1.csv').open('w') as f:
        writer=csv.writer(f);writer.writerow(['checkpoint_id','vehicle_type','observed_at','position','band','predicted_minutes','actual_minutes','absolute_error','confidence'])
        for p in predictions:writer.writerow([*p['group'],iso(p['at']),p['position'],p['band'],p['result']['eta'],p['actual'],p['error'],p['result']['confidence']])
    # Baselines contain only aggregate numbers, never vehicle or installation identifiers.
    assert not any(k in json.dumps(artifact).lower() for k in ['registration_number','regnum','plate'])
    print(json.dumps(report,ensure_ascii=False,indent=2))

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--data-root',type=Path,required=True);p.add_argument('--source-sha',required=True);p.add_argument('--output',type=Path,default=Path('analysis/forecast_v1/output'));p.add_argument('--asset',type=Path,default=Path('app/src/main/assets/forecast_baselines_v1.json'));a=p.parse_args();run(a.data_root,a.source_sha,a.output,a.asset)
