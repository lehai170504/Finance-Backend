package com.homie.finance.config;

import com.homie.finance.entity.Category;
import com.homie.finance.entity.Transaction;
import com.homie.finance.entity.User;
import com.homie.finance.entity.Wallet;
import com.homie.finance.repository.CategoryRepository;
import com.homie.finance.repository.TransactionRepository;
import com.homie.finance.repository.UserRepository;
import com.homie.finance.repository.WalletRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class DatabaseSeeder implements CommandLineRunner {

    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {

        if (categoryRepository.count() == 0) {
            System.out.println("🌱 Database đang trống! Bắt đầu gieo hạt (Data Seeding)...");

            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("123456"));
            admin.setEmail("admin@homie.com");
            admin.setRole(User.Role.ADMIN);
            userRepository.save(admin);
            System.out.println("✅ Đã tạo User: admin / 123456");

            Category food = new Category();
            food.setName("Ăn uống"); food.setType("EXPENSE"); food.setIcon("ic_fastfood");

            Category transport = new Category();
            transport.setName("Di chuyển"); transport.setType("EXPENSE"); transport.setIcon("ic_directions_car");

            Category shopping = new Category();
            shopping.setName("Mua sắm"); shopping.setType("EXPENSE"); shopping.setIcon("ic_shopping_bag");

            Category salary = new Category();
            salary.setName("Tiền lương"); salary.setType("INCOME"); salary.setIcon("ic_attach_money");

            Category transferOut = new Category();
            transferOut.setName("Chuyển tiền đi"); transferOut.setType("EXPENSE"); transferOut.setIcon("ic_swap_horiz");

            Category transferIn = new Category();
            transferIn.setName("Nhận tiền về"); transferIn.setType("INCOME"); transferIn.setIcon("ic_swap_horiz");

            categoryRepository.saveAll(List.of(food, transport, shopping, salary, transferOut, transferIn));
            System.out.println("✅ Đã tạo 6 danh mục mẫu.");

            Wallet cashWallet = new Wallet();
            cashWallet.setName("Tiền mặt");
            cashWallet.setBalance(5000000.0);
            cashWallet.setColor("#4CAF50");
            cashWallet.setUser(admin);
            walletRepository.save(cashWallet);
            System.out.println("✅ Đã tạo Wallet mẫu: Tiền mặt (5,000,000 VNĐ)");

            Transaction t1 = new Transaction();
            t1.setAmount(55000.0);
            t1.setNote("Ăn phở bò full topping");
            t1.setDate(LocalDate.now());
            t1.setCategory(food);
            t1.setWallet(cashWallet);
            t1.setUser(admin);

            Transaction t2 = new Transaction();
            t2.setAmount(15000000.0);
            t2.setNote("Lương tháng này");
            t2.setDate(LocalDate.now());
            t2.setCategory(salary);
            t2.setWallet(cashWallet);
            t2.setUser(admin);

            transactionRepository.saveAll(List.of(t1, t2));
            System.out.println("✅ Đã tạo 2 giao dịch mẫu cho Admin.");
            System.out.println("🚀 Hệ thống sẵn sàng chiến đấu! Lên Swagger test thôi homie!");
        }
    }
}