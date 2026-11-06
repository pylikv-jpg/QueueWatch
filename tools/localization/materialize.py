from pathlib import Path
import json,re
from xml.sax.saxutils import escape
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/'app/src/main/java/com/pylikv/queuewatch'
LANGS=['ru','en','de','pl','uk','be','lt','lv','et']
rows=[line.split('|') for line in Path(__file__).with_name('catalog.tsv').read_text().splitlines() if line]
assert all(len(row)==10 for row in rows)
lookup={r[1]:r[0] for r in rows}
for language in LANGS:
 folder=ROOT/'app/src/main/res'/('values' if language=='en' else 'values-'+language);folder.mkdir(parents=True,exist_ok=True)
 values=['<?xml version="1.0" encoding="utf-8"?>','<resources>']
 if language=='en': values.append('    <string name="app_name" translatable="false">QueueWatch</string>')
 for row in rows:
  value=escape(row[1+LANGS.index(language)]).replace("'","\\'")
  values.append(f'    <string name="{row[0]}">{value}</string>')
 cps=['Бенякони','Берестовица','Брест','Брузги','Григоровщина','Каменный Лог','Козловичи']
 if language not in ['ru','uk','be']:cps=['Benyakoni','Berestovitsa','Brest','Bruzgi','Grigorovshchina','Kamenny Log','Kozlovichi']
 if language=='uk':cps=['Беняконі','Берестовиця','Брест','Брузгі','Григорівщина','Кам’яний Лог','Козловичі']
 if language=='be':cps=['Беняконі','Бераставіца','Брэст','Брузгі','Грыгароўшчына','Каменны Лог','Казловічы']
 for key,value in zip(['benyakoni','berestovitsa','brest','bruzgi','grigorovshchina','kamenny_log','kozlovichi'],cps):values.append(f'    <string name="cp_{key}">{escape(value)}</string>')
 values.append('</resources>');(folder/'strings.xml').write_text('\n'.join(values)+'\n')
# Persisted status/alert labels can be translated immediately after switching.
ids=[r[0] for r in rows if '%' not in r[2] or r[0]=='position_reached']
(SRC/'LocalizedMessageIds.kt').write_text('package com.pylikv.queuewatch\n\ninternal object LocalizedMessageIds {\n    val values = intArrayOf(\n'+''.join('        R.string.'+i+',\n' for i in ids)+'    )\n}\n')
# Generated resources above are repeatable. Source transformation is a one-time migration.
def literals(s):
 i=0
 while i<len(s):
  if s.startswith('//',i):j=s.find('\n',i);i=len(s) if j<0 else j;continue
  if s.startswith('/*',i):j=s.find('*/',i+2);i=len(s) if j<0 else j+2;continue
  if s[i]!='"':i+=1;continue
  j=i+1
  while j<len(s):
   if s[j]=='\\':j+=2;continue
   if s[j]=='"':break
   j+=1
  yield i,j+1,s[i+1:j]
  i=j+1
for name in ['MainActivity.kt','SessionLauncherActivity.kt','QueueWatchService.kt','QueueAlertManager.kt','ForecastAnalyticsControl.kt']:
 f=SRC/name;s=f.read_text()
 for a,b,value in reversed(list(literals(s))):
  if value in lookup and '%' not in value:s=s[:a]+'L.text(R.string.'+lookup[value]+')'+s[b:]
 f.write_text(s)
print('Generated',len(rows)+7,'resources per locale for',len(LANGS),'languages')
