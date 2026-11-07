from pathlib import Path
import re
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
langs=['en','de','pl','uk','be','lt','lv','et','ru']
resources={}
for lang in langs:
 p=root/'app/src/main/res'/('values' if lang=='en' else 'values-'+lang)/'strings.xml'
 entries=ET.parse(p).getroot().findall('string')
 values={x.attrib['name']:''.join(x.itertext()) for x in entries}
 assert len(values)==len(entries),f'duplicate key: {lang}'
 assert all(value.strip() for value in values.values()),f'empty string: {lang}'
 if lang=='en': assert values.get('app_name')=='QueueWatch'
 else: assert 'app_name' not in values, 'Preview label must inherit its build-type override'
 resources[lang]={key:value for key,value in values.items() if key!='app_name'}
base=resources['en']
for lang,values in resources.items():
 assert values.keys()==base.keys(),f'missing/extra keys: {lang}'
 for key,value in values.items():
  assert sorted(re.findall(r'%\d+\$[ds]',value))==sorted(re.findall(r'%\d+\$[ds]',base[key])),(lang,key,'format arguments')
  assert not re.search(r'\$\{|(?<![0-9])\$[A-Za-z]',value),(lang,key,'Kotlin interpolation in XML')
registered={x.attrib['{http://schemas.android.com/apk/res/android}name'] for x in ET.parse(root/'app/src/main/res/xml/locale_config.xml').getroot()}
assert registered==set(langs),registered
# Check visible flows, excluding stable canonical checkpoint identifiers.
canonical={'Бенякони','Берестовица','Брест','Брузги','Григоровщина','Каменный Лог','Козловичи'}
for name in ['MainActivity.kt','SessionLauncherActivity.kt','QueueWatchService.kt','QueueAlertManager.kt','ForecastAnalyticsControl.kt']:
 s=(root/'app/src/main/java/com/pylikv/queuewatch'/name).read_text()
 s=re.sub(r'/\*.*?\*/|//[^\n]*','',s,flags=re.S)
 for value in re.findall(r'"((?:\\.|[^"\\])*)"',s):
  assert not re.search('[А-Яа-яЁё]',value) or value in canonical,(name,value)
print(f'Localization verified: {len(langs)} languages, {len(base)} string keys each; all format arguments match')
