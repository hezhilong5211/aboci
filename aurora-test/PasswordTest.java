import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordTest {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        
        // 数据库中的密码
        String storedPassword = "$2a$10$7JB720yubVSZv5W89pgBH.d.v.f.q.k.l.m.n.o.p.q.r.s.t.u.v";
        
        // 测试密码
        String testPassword = "admin123";
        
        System.out.println("数据库存储的密码: " + storedPassword);
        System.out.println("测试密码: " + testPassword);
        
        // 检查密码是否匹配
        boolean matches = encoder.matches(testPassword, storedPassword);
        System.out.println("密码匹配结果: " + matches);
        
        // 生成一个新的加密密码
        String encodedPassword = encoder.encode(testPassword);
        System.out.println("新生成的加密密码: " + encodedPassword);
        
        // 检查新生成的密码是否匹配
        boolean newMatches = encoder.matches(testPassword, encodedPassword);
        System.out.println("新密码匹配结果: " + newMatches);
    }
}
