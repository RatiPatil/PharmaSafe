// Reused by the isolated full workflow and focused public-page browser checks.
module.exports = async function verifyRoleLoginLayout(page, origin, roles) {
  await page.goto(`${origin}/#/home`);
  await page.getByRole('heading',{name:/Safer decisions.*Stronger together/}).waitFor();
  const nav=page.getByRole('navigation',{name:'Public site'});
  if(await nav.getByRole('link').count()!==3||await page.locator('#account-types').count())throw Error('Home must show separate user/college logins and student registration, without role cards');
  if(await nav.getByRole('link',{name:'College Administration Login',exact:true}).getAttribute('href')!=='#/login/college-administration'||await nav.getByRole('link',{name:'User Login',exact:true}).getAttribute('href')!=='#/login'||await nav.getByRole('link',{name:'Student registration',exact:true}).getAttribute('href')!=='#/students/register')throw Error('Home actions must use common login and student registration');
  const viewports = [[1920,880],[1440,900],[1366,768],[1280,590],[1024,640],[800,600],[390,844],[320,700]];
  for (const [width,height] of viewports) {
    await page.setViewportSize({width,height});
    let baseline;
    for (const role of roles) for (const mode of ['user','admin']) {
      await page.goto(`${origin}/#/access/${role.role}/${mode}`);
      await page.getByRole('heading',{name:`${role.name}${mode==='admin'?' Admin':''} sign in`,exact:true}).waitFor();
      const layout = await page.evaluate(() => {
        const rect=selector=>{const r=document.querySelector(selector).getBoundingClientRect();return {x:r.x,y:r.y,width:r.width,height:r.height,bottom:r.bottom};};
        const access=document.querySelector('.login-access');
        return {
          main:rect('.login-page'),panel:rect('.login-panel'),photo:rect('.login-photo'),
          username:rect('input[id$="-username"]'),button:rect('.login-submit'),
          horizontal:document.documentElement.scrollWidth>innerWidth+1,
          vertical:document.documentElement.scrollHeight>innerHeight+1,
          innerScroll:access.scrollHeight>access.clientHeight+1,
          animation:getComputedStyle(document.querySelector('.login-form-container')).animationName,
          fieldFont:parseFloat(getComputedStyle(document.querySelector('input[id$="-username"]')).fontSize),
        };
      });
      if(layout.horizontal)throw Error(`Role login horizontal overflow: ${role.role}/${mode} ${width}x${height}`);
      if(layout.animation!=='none'||layout.fieldFont<16)throw Error('Role login must avoid replayed animation and small input focus zoom');
      if(width>760 && (layout.vertical||layout.innerScroll||layout.panel.y<layout.main.y-1||layout.panel.bottom>layout.main.bottom+1))throw Error(`Role login does not fit: ${role.role}/${mode} ${width}x${height} ${JSON.stringify(layout)}`);
      if(!baseline)baseline=layout;
      for(const element of ['panel','photo','username','button'])for(const property of ['x','y','width','height']){
        if(Math.abs(layout[element][property]-baseline[element][property])>1)throw Error(`Role switch shifted ${element}.${property}: ${role.role}/${mode} ${width}x${height}`);
      }
    }
  }
  await page.setViewportSize({width:1440,height:900});
  for(const role of roles){
    await page.goto(`${origin}/#/register`);
    const link=page.getByRole('link',{name:`New registration for ${role.name}`,exact:true});
    if(await link.getAttribute('href')!==`#/access/${role.role}/register`)throw Error('Staff registration link must open the selected role directly');
    await link.click();
    await page.getByRole('heading',{name:`${role.name} registration`,exact:true}).waitFor();
  }
  console.log('PASS stable role/user/admin login card, photo and field positions across eight viewport sizes; all seven legacy staff registration links open the correct form directly');
};
