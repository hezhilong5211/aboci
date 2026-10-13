import java.util.regex.Pattern;

public class BCryptTest {
    public static void main(String[] args) {
        // 数据库中的密码
        String storedPassword = "$2a$10$7JB720yubVSZv5W89pgBH.d.v.f.q.k.l.m.n.o.p.q.r.s.t.u.v";
        
        System.out.println("数据库密码: " + storedPassword);
        System.out.println("密码长度: " + storedPassword.length());
        
        // 检查BCrypt密码格式
        // BCrypt密码应该以 $2a$, $2b$, 或 $2y$ 开头
        // 然后是cost参数 (2位数字)
        // 然后是22个字符的salt
        // 然后是31个字符的hash
        // 总共应该是60个字符
        
        if (storedPassword.length() != 60) {
            System.out.println("错误: 密码长度不是60个字符");
        }
        
        // 检查格式
        if (!storedPassword.startsWith("$2a$") && 
            !storedPassword.startsWith("$2b$") && 
            !storedPassword.startsWith("$2y$")) {
            System.out.println("错误: 密码不是有效的BCrypt格式");
        }
        
        // 提取cost参数
        String costPart = storedPassword.substring(4, 6);
        System.out.println("Cost参数: " + costPart);
        
        // 提取salt和hash部分
        String saltAndHash = storedPassword.substring(7);
        System.out.println("Salt和Hash部分: " + saltAndHash);
        System.out.println("Salt和Hash长度: " + saltAndHash.length());
        
        // 正常的BCrypt salt应该是22个字符，hash应该是31个字符
        // 总共53个字符
        
        // 检查密码中的点号
        int dotCount = 0;
        for (char c : storedPassword.toCharArray()) {
            if (c == '.') {
                dotCount++;
            }
        }
        System.out.println("密码中点号的数量: " + dotCount);
        
        // 分析问题
        System.out.println("\n问题分析:");
        System.out.println("1. 密码格式: " + storedPassword);
        System.out.println("2. 密码包含多个点号，这看起来不正常");
        System.out.println("3. BCrypt密码通常不应该包含这么多点号");
        System.out.println("4. 可能是数据库初始化脚本中的密码被截断或格式错误");
        
        // 建议的修复密码（admin123的BCrypt加密）
        String correctPassword = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
        System.out.println("\n正确的BCrypt密码示例 (admin123): " + correctPassword);
        System.out.println("正确密码长度: " + correctPassword.length());
    }
}
