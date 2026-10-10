'use strict';
const revision='98d84b8a06957e193a9a8e58b40590736c94bf70';
const base=`https://github.com/steipete/CodexBar/blob/${revision}/Sources/CodexBar/`;
const initial={fill:'Remaining',reset:'Countdown',pace:true,workdays:'Automatic',markers:true,credits:true,layout:'Stacked',refresh:'30 minutes',open:false,status:true,language:'System',currency:'USD',depleted:false,threshold:false,predictive:false,resetNotify:false,confetti:false,hidden:false,session:true,weekly:true,color:'#278866',window:'Auto',overview:'Detailed',sample:'Fresh',account:'Personal',range:'7 days',colorPace:false};
let state={...initial},scene='overview';
const scenes=[
 ['overview','使用与账户','Menu card → 手机首页','账户、额度、重置、刷新和失败状态，放在同一条使用路径。','MenuCardView.swift','MenuCardMetricRow.swift'],
 ['settings','设置导航','Settings sidebar → 设置目录','Mac 的页面分组映射到手机钻取导航，不另创一套设置分类。','PreferencesSidebar.swift'],
 ['general','General','General → 通用','刷新、语言、货币和服务状态保留语义；后台频率遵守 Android 限制。','PreferencesGeneralPane.swift'],
 ['menu','Menu / 显示','Menu → 显示','使用条、pace、Work days、重置方式及多账户布局属于同一个设置面。','PreferencesMenuPane.swift'],
 ['provider','Provider / 账户','Provider → 服务与账户','账户选择、来源、可见用量项、accent color 与窗口偏好。','PreferencesProviderDetailView.swift','PreferencesCodexAccountsSection.swift','ProviderAccentColorSettingsView.swift','ProviderMenuBarPercentWindowSettingsView.swift'],
 ['notifications','Notifications','Notifications → 通知','一一对应 Mac 的通知类型；当前 Android 恢复阈值不等于 Mac 完整 reset 行为。','PreferencesNotificationsPane.swift'],
 ['spend','Usage & Spend','Usage & Spend → 用量与花费','保留时间范围、日账本和热力图层级；无数据来源时显示不可用。','PreferencesSpendDashboardPane.swift'],
 ['menubar','Menu Bar / Widgets','Menu Bar → 系统速览','展示信息语义的对应，不把 macOS 菜单栏设置原封不动塞进手机。','PreferencesMenuBarPane.swift','PreferencesMenuPane.swift'],
 ['advanced','Advanced','Advanced → 高级','隐私显示可对应；CLI 安装、Keychain 和桌面存储统计需要区分平台。','PreferencesAdvancedPane.swift'],
 ['about','About','About → 关于','版本、来源、许可保留；Mac 更新方式与 Android APK 分发分开。','PreferencesAboutPane.swift'],
 ['platform','Mac 专属与暂缓','完整范围 → 明确边界','记录当前 Mac 的其他页面，避免遗漏，也避免为了对齐而创造新系统。','PreferencesSidebar.swift','PreferencesICloudSyncPane.swift','PreferencesHooksPane.swift','PreferencesPluginsPane.swift']
];
const byId=Object.fromEntries(scenes.map(s=>[s[0],s]));
const $=id=>document.getElementById(id);
const esc=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const button=(label,action)=>`<button type="button" data-action="${action}">${label}</button>`;
function setting(key,label,options,note=''){
 const id=`${renderSide}-${key}`;
 const control=Array.isArray(options)?`<select id="${id}" data-key="${key}">${options.map(v=>`<option${state[key]===v?' selected':''}>${esc(v)}</option>`).join('')}</select>`:options==='color'?`<input id="${id}" type="color" data-key="${key}" value="${state[key]}">`:`<input id="${id}" type="checkbox" role="switch" data-key="${key}"${state[key]?' checked':''}>`;
 return `<div class="setting"><label for="${id}">${label}${note?`<small>${note}</small>`:''}</label>${control}</div>`;
}
const group=(title,body)=>`<div class="section-label">${title}</div><div class="group">${body}</div>`;
const info=(label,value,note='')=>`<div class="setting"><div class="label">${label}${note?`<small>${note}</small>`:''}</div><span class="tag">${value}</span></div>`;
const route=(label,to)=>`<button class="link-row" data-action="go:${to}">${label}<span>›</span></button>`;
let renderSide='mac';
function metric(name,used,reset){
 const pct=state.fill==='Remaining'?100-used:used;
 return `<div class="metric"><div class="metric-head"><strong>${name} ${pct}% ${state.fill==='Remaining'?'left':'used'}</strong><small>${state.reset==='Countdown'?`Resets in ${reset}`:'Resets Oct 12, 21:41'}</small></div><div class="bar"><span style="width:${pct}%;background:${state.color}"></span>${state.pace&&name==='Weekly'?'<i aria-hidden="true"></i>':''}</div>${state.pace&&name==='Weekly'?'<div class="meta">7% in deficit · Runs out in 2d 15h</div>':''}</div>`;
}
function quota(){
 const err=state.sample==='Sign-in required',stale=state.sample==='Offline';
 return `<div class="card"><div class="account-head"><div class="provider-icon" aria-hidden="true">C</div><div class="identity"><strong>Codex</strong><small>${state.hidden?'Account hidden':state.account+' · demo@example.test'}</small></div><span class="badge">Pro</span></div>${err?'<p class="warning">Sign in again</p><p class="hint">保留账户；不把登录失效显示为 0% 额度。</p>'+button('Reconnect','connect'):(state.session?metric('Session',32,'3h 24m'):'')+(state.weekly?metric('Weekly',57,'3d 12h'):'')}${stale?'<div class="state-message">Offline · Showing last successful reading</div>':''}<div class="meta">${stale?'Updated 2h ago':err?'Authentication required':'Updated just now'}</div>${!err&&state.credits?'<div class="setting"><div class="label">Credits<small>示意 provider-reported units</small></div><span>12.00 credits</span></div>':''}</div>`;
}
function overview(side){
 return `<div class="tabrow">${button('Overview','account:Personal')}${button('Codex','account:Personal')}</div>${group('Account',setting('account','Account',['Personal','Work'],'演示双账户切换；额度数值为共用占位样本'))}${quota()}<div class="action-row">${button('Refresh','refresh')}${button('Settings…','go:settings')}${button('Usage & Spend…','go:spend')}</div>${group('状态演示 · review controls',setting('sample','Reading state',['Fresh','Offline','Sign-in required']))}<p class="hint">${side==='mac'?'点击菜单栏 → 选择服务/账户 → 查看额度或打开设置。':'打开 App → 选择服务/账户 → 查看额度；设置入口通往同语义的设置页。'}数值与 plan 均为演示。</p>`;
}
function content(id,side){
 const mobile=side==='mobile';
 switch(id){
 case 'overview':return overview(side);
 case 'settings':return group('Settings',['general','spend','notifications','menu','provider','advanced','about'].map(k=>route(byId[k][1],k)).join(''))+group(mobile?'系统速览 · Android 适配':'Menu Bar',route(mobile?'Widgets / 系统入口':'Menu Bar','menubar'))+group('平台范围',route('iCloud Sync / Hooks / Plugins / Debug','platform'))+'<p class="hint">手机按页钻取；宽屏可恢复左侧目录＋右侧详情。设置名称与信息架构来自 Mac，不新增独立 Pace 分组。</p>';
 case 'general':return group('System',setting('language','Language',['System','English','简体中文'],'待对齐 · 示例语言选项')+setting('currency','Preferred Currency',['USD','EUR','JPY'],'需数据 · 当前 Android 无汇率转换')+(mobile?'':info('Default Terminal','Terminal')+info('Launch at login','macOS')))+group('Refreshing',setting('refresh','Refresh interval',mobile?['Manual','15 minutes','30 minutes','60 minutes']:['Manual','5 minutes','15 minutes','30 minutes','60 minutes'],'已有基础 · Android periodic work 最短 15 分钟')+setting('open','Refresh on open',true,'Mac 默認關；關閉此選項時僅補救上次失敗或尚無用量的帳戶，開啟時每次開啟都全部重新整理 · Android 已按此實作（源碼級，待設備驗收）')+setting('status','Check provider status',true,'Mac 默認開；關閉立即隱藏狀態、停止輪詢並保留最後成功狀態 · Android 已按此實作（源碼級，待設備驗收）'))+group('Preferences transfer',info('Import / Export settings','后续','Mac 有偏好导入/导出；Android 格式与凭证边界待核查'))+'<p class="hint">这里调整演示偏好，不发起后台任务或实际货币换算。Mac Low Power Mode / keyboard shortcut 保留在平台范围，不伪造移动端行为。</p>';
 case 'menu':return group('Usage',setting('fill','Usage bars fill',['Remaining','Used'],'待对齐')+setting('markers','Quota warning markers',true,'需对齐具体标记规则')+setting('pace','Show pace',true,'待对齐 · 示例 pace 并非现有线性估计')+setting('workdays','Work days',['Automatic','4','5','7'],'待对齐 · 此原型不计算工作日模型')+setting('reset','Reset times',['Countdown','Absolute'],'已有 profile 基础'))+group('Content',setting('overview','Overview layout',['Detailed','Compact'],'待对齐')+setting('credits','Show credits and extra usage',true,'已有 amounts 开关基础')+setting('layout','Multi-account layout',['Switcher','Stacked'],'待对齐'))+quota()+'<p class="hint">改变 Remaining / Used、reset 或 pace 后，两侧示例一起更新。Work days、layout 等只展示设置选择；未模拟其算法或布局。</p>';
 case 'provider':return group('Codex',setting('account','Account',['Personal','Work'],'已有多账户基础')+info('Usage source','OAuth','对应 Android 当前支持的来源；不展示未支持的浏览器 cookie 来源')+`<div class="setting"><span class="label">Authentication<small>浏览器交互由 Android 管理，验证成功才保存</small></span>${button('Reconnect','connect')}</div>`)+group('Visible usage items',setting('session','Session',true,'已有稳定窗口 ID 基础')+setting('weekly','Weekly',true,'隐藏仅影响显示，不改变抓取或风险计算')+`<div class="setting">${button('Restore Defaults','restore-items')}</div>`)+group('Appearance',setting('color','Accent color','color','待对齐 · 可修改演示 bar 色')+setting('window',mobile?'Widget percent window':'Menu bar percent window',['Auto','Session','Weekly'],'Android 适配提案，未实现'))+quota();
 case 'notifications':return group('Alerts',setting('depleted','Quota depleted',true,'待对齐')+setting('threshold','Threshold warnings',true,'待对齐')+setting('predictive','Predictive pace warnings',true,'需 Mac pace 数据/规则')+setting('resetNotify','Reset notifications',true,'需确认 reset 证据与账户去重'))+group('Celebrations',setting('confetti','Confetti on reset',true,'Mac 有此项；Android 暂缓，此开关仅示意'))+(mobile?'<div class="state-message">Android 系统通知权限<br>启用通知时请求权限；未授权时回到系统设置。</div>':'<div class="state-message">Notification delivery follows macOS permissions.</div>')+'<p class="hint">示意开关初始全部关闭。现在 App 的 measured recovery 阈值规则不能当作四类 Mac 通知已经完成；不再另造 “quota restored” 产品分组。</p>';
 case 'spend':return group('Usage & Spend',setting('range','Time range',['7 days','30 days'],'需数据 · 当前 Android 没有完整 spend ledger'))+`<div class="card"><span class="tag">示意 · 非真实花费</span><p class="hint">Total spend · USD</p><div class="big-number">$7.42</div><div class="chart" role="img" aria-label="七天花费示意柱状图，无真实数据">${[25,47,34,74,42,61,90].map(h=>`<span style="height:${h}%"></span>`).join('')}</div><div class="chart-labels"><span>Oct 2</span><span>Oct 8</span></div><div class="heatmap" role="img" aria-label="活动热力图示意，无真实数据">${Array.from({length:42},(_,i)=>`<i style="opacity:${.12+(i*7%9)/12}"></i>`).join('')}</div></div>`+group('Daily ledger',info('Oct 8','$1.84')+info('Oct 7','$1.26')+info('Oct 6','$0.87')+`<div class="setting">${button('Show all','ledger')}</div>`)+`<div class="state-message">${mobile?'数据尚未连接时：Spend unavailable。不可用 balance 差值伪造每日成本。':'Mac 可结合其支持的 provider/local 数据。具体来源决定完整性。'}</div><p class="hint">右侧是有数据后才启用的目标页面。时间选择只演示控件，不改变占位数据；不同币种分开，不把缺失值当零。</p>`;
 case 'menubar':return mobile?group('Widget · proposed mapping',setting('account','Pinned account',['Personal','Work'])+setting('window','Percent window',['Auto','Session','Weekly'],'待对齐')+setting('colorPace','Color Pace Indicator',true,'Mac 图标语义的 widget 映射，待确认'))+quota()+'<div class="state-message">系统入口沿用现有 Android widget / tile / notification。菜单栏 token editor、Merge Icons 与显示器对比度不在第一阶段复刻。</div>':group('Icon',info('Menu bar style','Provider icon')+setting('colorPace','Color Pace Indicator',true))+group('Layout',info('Layout tokens','Identity · Usage · Time','Mac 支持 token 排列；此原型不实现编辑器')+info('Merge Icons','Enabled')+info('Overview providers','Up to 6'))+quota();
 case 'advanced':return group('Privacy',setting('hidden','Hide personal info',true,'待对齐 · 仅改变演示账户身份行')+(mobile?'':info('Disable Keychain access','macOS','影响桌面 cookie / credential discovery')))+group('Diagnostics',info('Show provider storage usage',mobile?'暂缓':'macOS source homes')+info('Show debug settings','暂缓'))+(mobile?'<p class="hint">Android 加密存储只承担凭证保护，不等价于 Mac Keychain/browser 自动发现。不会为了对应而加入不存在的 cookie 扫描能力。</p>':group('Command Line',info('Install CLI','macOS executable')))+quota();
 case 'about':return `<div class="card"><div class="provider-icon" aria-hidden="true">C</div><h2>CodexBar</h2><p class="hint">${mobile?'Unofficial Android port':'macOS menu bar usage monitor'}</p></div>`+group('About',info('Version',mobile?'0.0.5-beta-native':'reference checkout')+info(mobile?'Bundled Core/CLI':'Source revision',mobile?'v0.71.0':revision.slice(0,12))+`<div class="setting"><span class="label">Source & licenses</span><a href="https://github.com/${mobile?'Lee20171010/CodexBar-android':'steipete/CodexBar'}" target="_blank" rel="noreferrer">GitHub ↗</a></div>`)+(mobile?group('Distribution',info('Updates','Signed APK','保留 Android 签名身份；不显示 brew 命令')):group('Homebrew Updates',info('Check for Updates','macOS','可包含自动检查与 brew 升级提示')));
 case 'platform':return group('Mac settings inventory',info('iCloud Sync',mobile?'暂缓':'Mac pane','Android 不自动获得 CloudKit 同步')+info('Hooks',mobile?'暂缓':'Mac pane','命令执行与凭证边界需独立设计')+info('Plugins',mobile?'暂缓':'Mac pane','不是当前 Android 已开放的插件系统')+info('Debug',mobile?'暂缓':'Conditional pane','Mac 由 debug 设置控制可见性'))+group('Desktop-only controls',info('Default Terminal','macOS')+info('Keychain policies','macOS')+info('Homebrew updates','macOS')+info('Launch at login / shortcut','macOS'))+'<p class="hint">这些是 Mac 确有的范围，不是要一次搬完。先完成共享 UI/UX，再评估平台能力；不另加 Android 云同步、插件商店或自动化系统。</p>';
 }
}
const mappings={overview:'服务 → 账户 → 额度 → 刷新/设置。保持同样的 source、更新时间和 unknown 语义；手机不用 hover，明确提供点击入口。',settings:'Mac 常驻侧栏 → 手机设置目录 → 子页 → 返回。General / Menu / Notifications / Provider 等分组按 Mac 对应；宽屏可用双栏。',general:'设置语义保持一致。刷新频率需映射 Android WorkManager 的最短 15 分钟与 best-effort 约束，不能承诺 Mac 级别的后台计时。',menu:'Mac Menu 页直接对应手机显示页。现有 remaining/reset/amounts 基础可复用；used 切换、工作日模型与 Mac pace 需要实现。',provider:'账户与 provider 偏好同页关联。可见用量项沿用稳定 ID；source 列表只显示 Android 已支持来源，不能因 CLI 包含 provider 就宣称登录可用。',notifications:'逐类告警匹配 Mac 条件，Android 增加平台必须的权限处理。未来每种告警要有独立的测试与去重证明。',spend:'Mac 的花费看板不是当前 Android burn-down 图的换皮。必须先核查 provider 输出、历史与币种，再开放 UI。',menubar:'复用用户查看同一份额度的目的。手机已有 widget 是优先承载面，桌面菜单栏排版工具暂缓；不是直接复制所有 macOS 控件。',advanced:'隐藏身份可以映射；Keychain、CLI install 等属于平台功能。不要用 Android 加密存储假称这些桌面能力已被实现。',about:'身份、版本与许可保持清晰。Mac UI reference 与 bundled engine 是两个独立 revision，未来升级分别验证。',platform:'完整列出 Mac 当前范围，但不把暂缓项目写成已实现。优先让核心使用、设置、账户和显示达到一致。'};
function render(){
 const s=byId[scene];
 $('topics').innerHTML=scenes.map(([id,title])=>`<button data-action="go:${id}" aria-current="${id===scene}">${title}</button>`).join('');
 $('scene-number').textContent=`${String(scenes.indexOf(s)+1).padStart(2,'0')} / ${String(scenes.length).padStart(2,'0')}`;
 $('scene-title').textContent=s[2];$('scene-summary').textContent=s[3];
 renderSide='mac';const macContent=content(scene,'mac');
 $('mac').innerHTML=scene==='overview'?`<div class="menu-stage"><div class="menubar"><span>CodexBar ◒ 43%</span><span>Thu 09:41</span></div><div class="popover">${macContent}</div></div>`:`<div class="mac-window"><div class="mac-title"><span class="lights" aria-hidden="true"><i></i><i></i><i></i></span>CodexBar Settings</div><div class="mac-layout"><nav class="mac-nav" aria-label="Mac 设置">${['general','spend','notifications','menubar','menu','advanced','platform','about'].map(k=>`<button class="${k===scene?'active':''}" data-action="go:${k}">${byId[k][1]}</button>`).join('')}<div class="nav-label">Providers</div><button data-action="go:provider" class="${scene==='provider'?'active':''}">Codex</button></nav><div class="mac-content"><h3>${s[1]}</h3>${macContent}</div></div></div>`;
 renderSide='mobile';$('mobile').innerHTML=`<div class="phone-header">${button(scene==='overview'?'设置':'←',scene==='overview'?'go:settings':scene==='settings'?'go:overview':'go:settings')}<h3>${scene==='overview'?'CodexBar':s[1]}</h3>${scene!=='overview'?button('首页','go:overview'):''}</div><div class="phone-content">${content(scene,'mobile')}</div>`;
 $('mapping').textContent=mappings[scene];$('evidence').innerHTML=s.slice(4).map(p=>`<a href="${base+p}" target="_blank" rel="noreferrer">${p} ↗</a>`).join('');
}
document.addEventListener('change',e=>{
 const k=e.target.dataset.key;if(!k)return;
 const focus=e.target.id;state[k]=e.target.type==='checkbox'?e.target.checked:e.target.value;
 // Android's periodic floor applies to the shared review state.
 if(k==='refresh'&&state.refresh==='5 minutes'){state.refresh='15 minutes';$('feedback').textContent='Mac 可选 5 分钟；Android 周期任务最短 15 分钟，本原型映射为 15 分钟。';}
 else if(k==='status'){$('feedback').textContent='開關示範：關閉 → 狀態行與徽章立即隱藏、保留最後成功狀態、不再輪詢；額度刷新不受影響。未寫入 App 或任何帳戶。';}
 else $('feedback').textContent=`演示设置已同步：${k} = ${state[k]}。未写入 App 或任何账户。`;
 render();document.getElementById(focus)?.focus();
});
document.addEventListener('click',e=>{
 const target=e.target.closest('[data-action]');if(!target)return;const action=target.dataset.action;
 if(action.startsWith('go:')){scene=action.slice(3);render();$('feedback').textContent=`已切换到 ${byId[scene][1]}：左 Mac / 右 mobile 同场景。`;}
 else if(action==='refresh'){$('feedback').textContent='演示：合并重复刷新 → 更新 reading/更新时间。没有发出网络请求。';state.sample='Fresh';render();}
 else if(action==='connect'){$('feedback').textContent='目标流程：浏览器授权 → 返回 App → 验证额度 → 保存。取消或失败保留现有账户；本页不启动登录。';}
 else if(action==='restore-items'){state.session=true;state.weekly=true;render();$('feedback').textContent='Visible usage items 已恢复演示初始值；两侧窗口重新出现。';}
 else if(action==='ledger'){$('feedback').textContent='目标流程：Show all 展开完整日账本。当前只有占位数据，未伪造其他日期。';}
 else if(action.startsWith('account:')){state.account=action.slice(8);render();}
});
$('theme').addEventListener('click',()=>{const dark=document.body.classList.toggle('dark');$('theme').textContent=dark?'切换浅色预览':'切换深色预览';});
$('reset-demo').addEventListener('click',()=>{state={...initial};render();$('feedback').textContent='演示状态已恢复；不是 Mac 或 Android 的产品默认值声明。';});
render();
