"""One bounded public Open-Meteo request; never sends personal coordinates or API keys."""
from pathlib import Path
from datetime import datetime,timezone
import urllib.request,urllib.parse,json,time
out=Path('work/check205');out.mkdir(parents=True,exist_ok=True)
params={'latitude':'39.9042','longitude':'116.4074','current':'temperature_2m,weather_code','daily':'temperature_2m_max,temperature_2m_min,weather_code','forecast_days':'7','timezone':'Asia/Shanghai','timeformat':'unixtime'}
url='https://api.open-meteo.com/v1/forecast?'+urllib.parse.urlencode(params)
result={'testedAt':datetime.now(timezone.utc).isoformat(),'endpoint':url,'location':'public Beijing city-centre coordinates, not user location','passed':False,'scope':'public endpoint connectivity and response schema from GitHub runner, not a phone-network guarantee'}
t=time.monotonic()
try:
    req=urllib.request.Request(url,headers={'User-Agent':'Four-Seasons-Luke-review-check/2.0.5'})
    with urllib.request.urlopen(req,timeout=25) as r:
        result['status']=r.status;data=json.loads(r.read(2_000_000))
    assert result['status']==200
    assert isinstance(data['current']['temperature_2m'],(int,float))
    assert isinstance(data['current']['weather_code'],(int,float))
    assert len(data['daily']['time'])==7 and len(data['daily']['temperature_2m_max'])==7
    assert abs(data['current']['time']-time.time())<24*3600
    result['passed']=True;result['responseTime']=data['current']['time'];result['forecastDays']=len(data['daily']['time'])
except Exception as e:result['error']=str(e)
result['elapsedSeconds']=round(time.monotonic()-t,3)
(out/'live-weather.json').write_text(json.dumps(result,ensure_ascii=False,indent=2));print(json.dumps(result,ensure_ascii=False,indent=2))
