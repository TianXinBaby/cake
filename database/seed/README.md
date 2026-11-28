# 课程演示种子（可提交）

本目录是**仓库内可提交**的演示数据，供组员和展示电脑还原“奶油星球”页面。

它和 `database/demo/` 的区别：

- `database/seed/`：可提交，SQL 中只有 BCrypt 哈希，没有明文密码
- `database/demo/`：本地临时脚本，含明文演示密码，已被 `.gitignore` 排除

## 包含内容

- 商家账号：`13900000001`（MERCHANT）
- 密码：**不写入仓库**，由项目负责人在小组内单独提供
- 店铺：奶油星球（电话 18800188800，地址：星空）
- 分类 4 个：奶油蛋糕、慕斯蛋糕、芝士蛋糕、节日限定
- 商品 8 个，图片使用 `frontend/public/images/cake-01.png`～`cake-08.png`
  - 原味重芝士蛋糕：库存 0、上架，顾客端显示“已售罄”
  - 红丝绒蛋糕：下架，商家端可见
  - 其余为上架商品

## 使用前提

1. MySQL 8.0.x 已启动。
2. 数据库已按 `database/schema`、`database/indexes`、`database/constraints` 初始化六张表。
3. 演示账号 `13900000001` 不存在；已存在时脚本会失败，不会覆盖。

## 执行

在项目根目录打开 PowerShell，按自己的 MySQL 路径、库名和用户名替换：

```powershell
Set-Location -LiteralPath 'D:\1Webapplication\FinalPro\cake'
$mysql = 'D:\dev-tool-installers\Mysql\MySQL\MySQL Server 8.0\bin\mysql.exe'
$sql = (Resolve-Path 'database\seed\05_demo_seed.sql').Path
cmd /c "`"$mysql`" --host=127.0.0.1 --port=3306 --user=root --protocol=TCP --default-character-set=utf8mb4 -p cake_order_system < `"$sql`""
```

执行成功后会输出：

```text
merchant_id  user_id  product_count  category_count  on_sale_count
1            1        8              4               7
```

## 重新演示 / 清理

```powershell
$mysql = 'D:\dev-tool-installers\Mysql\MySQL\MySQL Server 8.0\bin\mysql.exe'
$remove = (Resolve-Path 'database\seed\06_remove_demo_seed.sql').Path
cmd /c "`"$mysql`" --host=127.0.0.1 --port=3306 --user=root --protocol=TCP -p cake_order_system < `"$remove`""
```

清理脚本只按固定演示账号删除该店铺的订单、商品、分类和账号，不触碰其他数据。

## 安全说明

- 本种子仅用于课程本地演示，不要用于公网或生产环境。
- 仓库只保存 BCrypt 哈希；明文密码不要提交、不要写进 SQL 注释。
- 如果小组需要改密码，请在本地重新生成 BCrypt 哈希后替换账号记录，不要提交明文。
