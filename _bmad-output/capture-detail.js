const { chromium } = require('playwright');

(async () => {
    const browser = await chromium.launch({ headless: false });
    const context = await browser.newContext();
    const page = await context.newPage();
    
    const requests = [];
    
    // 监听所有网络请求
    page.on('request', request => {
        if (request.url().includes('/api/')) {
            const data = {
                url: request.url(),
                method: request.method(),
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
        if (response.url().includes('/api/')) {
            console.log(`📥 RESPONSE: ${response.status()} ${response.url()}`);
            try {
                const body = await response.text();
                if (body.length < 3000) {
                    console.log(`   Body: ${body.substring(0, 2000)}`);
                }
            } catch (e) {}
        }
    });
    
    // 打开登录页
    console.log('\n=== 打开登录页 ===');
    await page.goto('http://192.168.3.20:13381/v');
    await page.waitForTimeout(3000);
    
    // 检查是否需要登录
    const loginInput = await page.$('input[type="text"], input[name="username"]');
    if (loginInput) {
        console.log('\n=== 需要登录，开始登录 ===');
        await page.fill('input[type="text"], input[name="username"]', 'duanhongke');
        await page.fill('input[type="password"]', 'Hongke688.');
        await page.click('button[type="submit"], button:has-text("登录"), .login-btn');
        await page.waitForTimeout(5000);
    }
    
    // 等待首页加载
    console.log('\n=== 等待首页加载 ===');
    await page.waitForTimeout(3000);
    await page.screenshot({ path: 'nastv/_bmad-output/detail-01-home.png' });
    
    // 直接导航到一个电视剧详情页 (大生意人)
    console.log('\n=== 导航到电视剧详情页 ===');
    // 从之前捕获的数据中，我们知道有一个电视剧 guid: 0947ca73d69047e48a88eb3908153037
    await page.goto('http://192.168.3.20:13381/v/detail/0947ca73d69047e48a88eb3908153037');
    await page.waitForTimeout(5000);
    await page.screenshot({ path: 'nastv/_bmad-output/detail-02-tv-detail.png' });
    console.log('\n=== 电视剧详情页（第一层）截图已保存 ===');
    
    // 等待更多请求
    await page.waitForTimeout(3000);
    
    // 尝试点击一季
    console.log('\n=== 尝试点击季 ===');
    const seasonTabs = await page.$$('.semi-tabs-tab, [class*="season"]');
    console.log(`找到 ${seasonTabs.length} 个季标签`);
    
    // 尝试点击一集
    console.log('\n=== 尝试点击集 ===');
    const episodeItems = await page.$$('[class*="episode"], [class*="ep-item"], .semi-list-item, [class*="item"]');
    console.log(`找到 ${episodeItems.length} 个集项目`);
    
    if (episodeItems.length > 0) {
        // 点击第一集
        await episodeItems[0].click();
        await page.waitForTimeout(5000);
        await page.screenshot({ path: 'nastv/_bmad-output/detail-03-episode-detail.png' });
        console.log('\n=== 剧集详情页（第二层）截图已保存 ===');
    }
    
    // 输出所有捕获的请求
    console.log('\n\n========== 捕获的API请求汇总 ==========');
    requests.forEach((req, i) => {
        console.log(`\n[${i+1}] ${req.method} ${req.url}`);
        if (req.postData) console.log(`    Body: ${req.postData}`);
    });
    
    // 保持浏览器打开
    console.log('\n=== 浏览器将在90秒后关闭 ===');
    await page.waitForTimeout(90000);
    
    await browser.close();
})();
