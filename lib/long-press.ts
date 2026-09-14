export const LONG_PRESS_MS = 480;
export const DRAG_TOLERANCE = 10;
type Scheduler = { set: (fn: () => void, ms: number) => unknown; clear: (id: unknown) => void; now: () => number };
export function longPressController(open: (id: string) => void, clock: Scheduler) {
 let timer:unknown, pointer:number|null=null, x=0, y=0, armed='', suppressed='', until=0;
 const cancel=()=>{if(timer!==undefined)clock.clear(timer);timer=undefined;pointer=null;armed='';};
 return {
  start(id:string,pid:number,px:number,py:number,primary=true){cancel();suppressed='';if(!primary)return;x=px;y=py;pointer=pid;armed=id;
   timer=clock.set(()=>{timer=undefined;suppressed=id;until=clock.now()+900;armed='';open(id);},LONG_PRESS_MS);},
  move(pid:number,px:number,py:number){if(pid===pointer&&Math.hypot(px-x,py-y)>DRAG_TOLERANCE)cancel();},
  cancel,
  consume(id:string,keyboard=false){if(keyboard||suppressed!==id||clock.now()>until)return false;suppressed='';return true;},
  context(id:string){cancel();if(suppressed===id&&clock.now()<=until)return;suppressed=id;until=clock.now()+900;open(id);},
  get armed(){return !!armed;}
 };
}
