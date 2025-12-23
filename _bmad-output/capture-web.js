const { chromium } = require('playwright');

(async () => {
    const browser = await chromium.launch({ headless: false });
    const context = await browser.newContext();
    const page = await context.newPage();
    
    const requests = [];
    
    // 监听所有网络请求
    page.on('request', request => {
        if (request.url().includes('/api/') || request.url().includes('/v/')) {
            const data = {
                url: request.url(),
                method: request.method(),
                headers: request.headers(),
                postData: request.postData()
            };
            requests.push(data);
            console.log(`\n📤 REQUEST: ${request.method()} ${request.url()}`);
            if (request.postData()) {
                console.log(`   Body: ${request.postData()}`);
            }
        }
    });
    
    page.on('response', async response => {
        if (response.url().includes('/api/') || response.url().includes('/v/')) {
            console.log(`📥 RESPONSE: ${response.status()} ${response.url()}`);
            try {
                const body = await response.text();
                if (body.length < 2000) {
                    console.log(`   Body: ${body.substring(0, 500)}`);
                }
            } catch (e) {}
        }
    });
    
    // 打开登录页
    console.log('\n=== 打开网页 ===');
    await page.goto('http://192.168.3.20:13381/v');
    await page.waitForTimeout(3000);
    
    // 截图
    await page.screenshot({ path: 'nastv/_bmad-output/01-login-page.png' });
    console.log('\n=== 登录页截图已保存 ===');
    
    // 输入用户名密码
    console.log('\n=== 开始登录 ===');
    await page.fill('input[type="text"], input[name="username"], input[placeholder*="用户"]', 'duanhongke');
    await page.fill('input[type="password"]', 'Hongke688.');
    await page.screenshot({ path: 'nastv/_bmad-output/02-filled-login.png' });
    
    // 点击登录
    await page.click('button[type="submit"], button:has-text("登录"), .login-btn');
    await page.waitForTimeout(5000);
    await page.screenshot({ path: 'nastv/_bmad-output/03-after-login.png' });
    
    console.log('\n=== 登录完成，等待首页加载 ===');
    await page.waitForTimeout(3000);
    await page.screenshot({ path: 'nastv/_bmad-output/04-home-page.png' });
    
    // 输出所有捕获的请求
    console.log('\n\n========== 捕获的API请求汇总 ==========');
    requests.forEach((req, i) => {
        console.log(`\n[${i+1}] ${req.method} ${req.url}`);
        if (req.postData) console.log(`    Body: ${req.postData}`);
    });
    
    // 保持浏览器打开一段时间以便观察
    console.log('\n=== 浏览器将在30秒后关闭 ===');
    await page.waitForTimeout(30000);
    
    await browser.close();
})();
