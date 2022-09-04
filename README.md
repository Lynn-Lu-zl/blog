# blog
基于SpringBoot + Vue 开发的前后端分离博客，采用SpringSecurity进行权限管理，ElasticSearch全文搜索，支持码云第三方登录、发布说说等功能。



# docker部署运行环境

## 1.必装环境（最低1核2G）

### 1.1.安装Docker

```
yum install -y yum-utils device-mapper-persistent-data lvm2    //安装必要工具
yum-config-manager --add-repo http://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo //设置yum源
yum install -y docker-ce  //下载docker
systemctl start docker   //启动docker

```

### 1.2.安装MySQL

```
docker pull mysql //下载MySQL镜像
docker run --name mysql --restart=always -p 3306:3306 -e MYSQL_ROOT_PASSWORD=密码 -d mysql //启动MySQL

```

### 1.3.安装Redis

```
docker pull redis //下载Redis镜像
docker run --name redis  --restart=always -p 6379:6379 -d redis --requirepass "密码" //启动Redis

```

### 1.4.安装nginx（请先部署项目再启动）

```
docker pull nginx //下载nginx镜像
docker run --name nginx --restart=always -p 80:80 -p 443:443 -d -v /usr/local/nginx/nginx.conf:/etc/nginx/nginx.conf -v /usr/local/vue:/usr/local/vue -v /usr/local/upload:/usr/local/upload nginx  //启动nginx，映射本地配置文件

```

### 1.5.安装RabbitMQ

```
docker pull rabbitmq:management //下载RabbitMQ镜像
docker run --name rabbit --restart=always -p 15672:15672 -p 5672:5672  -d  rabbitmq:management   //启动RabbitMQ,默认guest用户，密码也是guest。

```

## 2.选装环境（需2核4G）

### 2.1.安装elasticsearch （可切换为MYSQL搜索）

```
//下载elasticsearch镜像
docker pull elasticsearch:7.9.3 
//启动elasticsearch
docker run -d --name elasticsearch -p 9200:9200 -p 9300:9300 -e "discovery.type=single-node" -e ES_JAVA_OPTS="-Xms64m -Xmx512m" elasticsearch:7.9.3
//进入elasticsearch容器
docker exec -it elasticsearch /bin/bash
//安装ik分词器
./bin/elasticsearch-plugin install https://github.com/medcl/elasticsearch-analysis-ik/releases/download/v7.9.2/elasticsearch-analysis-ik-7.9.3.zip    

```

安装成功后使用postman创建索引

![QQ截图20210812211857.png](https://static.talkxj.com/articles/14d857e8906621347816792dc695b4f5.png)

JSON参数

```
{
    "mappings": {
            "properties": {
                "id": {
                    "type": "integer"
                },
                "articleTitle": {
                    "type": "text",
                    "analyzer": "ik_max_word"
                },
                "articleContent": {
                    "type": "text",
                    "analyzer": "ik_max_word"
                },
                "isDelete": {
                    "type": "integer"
                },
                "status": {
                    "type": "integer"
                }
            }
      }
}

```

查看索引结构

![QQ截图20210402215812.png](https://static.talkxj.com/articles/1617371955872.png)

如图所示则创建成功

### 2.2.安装MaxWell （ElasticSearch同步数据）

```
docker pull zendesk/maxwell //下载MaxWell镜像
docker run --name maxwell --restart=always  -d  zendesk/maxwell bin/maxwell  --user='数据库用户名' --password='数据库密码'  --host='IP地址'  --producer=rabbitmq --rabbitmq_user='MQ用户名' --rabbitmq_pass='MQ密码' --rabbitmq_host='IP地址' --rabbitmq_port='5672' --rabbitmq_exchange='maxwell_exchange'  --rabbitmq_exchange_type='fanout' --rabbitmq_exchange_durable='true' --filter='exclude: *.*, include: blog.tb_article.article_title = *, include: blog.tb_article.article_content = *, include: blog.tb_article.is_delete = *, include: blog.tb_article.status = *' //运行MaxWell

```

## 3.容器启动成功，截图如下

![QQ截图20200629175647.png](https://static.talkxj.com/articles/1593424717446.png)





# 部署项目



## 1.打包后端项目jar包

打开pom.xml文件，修改packaging方式为jar

![](https://static.talkxj.com/articles/25de8986f7af569e7586936b847f596a.png)



点击右侧maven插件 -> package

![QQ截图20210813231652 1.png](https://static.talkxj.com/articles/1f03c29f1da200e8bc7eab611697475b.png)

打包成功后会在target目录下生成jar包

![QQ截图20210813231721 1.png](https://static.talkxj.com/articles/a8c50ac983abf425dfccda924b4d0ab3.png)

## 2.编写Dockerfile文件

```
FROM java:8
VOLUME /tmp
ADD blog-springboot-0.0.1.jar blog.jar       
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/blog.jar"] 
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

```

**ps：Dockerfile文件不需要后缀，直接为文件格式**

## 3.编写blog-start.sh脚本

```
#源jar路径  
SOURCE_PATH=/usr/local/docker
#docker 镜像/容器名字或者jar名字 这里都命名为这个
SERVER_NAME=blog-springboot-0.0.1.jar
TAG=latest
SERVER_PORT=8080
#容器id
CID=$(docker ps | grep "$SERVER_NAME" | awk '{print $1}')
#镜像id
IID=$(docker images | grep "$SERVER_NAME:$TAG" | awk '{print $3}')
if [ -n "$CID" ]; then
  echo "存在容器$SERVER_NAME, CID-$CID"
  docker stop $CID
  docker rm $CID
fi
# 构建docker镜像
if [ -n "$IID" ]; then
  echo "存在$SERVER_NAME:$TAG镜像，IID=$IID"
  docker rmi $IID
else
  echo "不存在$SERVER_NAME:$TAG镜像，开始构建镜像"
  cd $SOURCE_PATH
  docker build -t $SERVER_NAME:$TAG .
fi
# 运行docker容器
docker run --name $SERVER_NAME -v /usr/local/upload:/usr/local/upload -d -p $SERVER_PORT:$SERVER_PORT $SERVER_NAME:$TAG
echo "$SERVER_NAME容器创建完成"

```

**ps：sh文件需要用notepad++转为Unix格式**

![QQ截图20210402213326.png](https://static.talkxj.com/articles/1617370849316.png)

## 4.将文件传输到服务器

![QQ截图20210916104127.png](https://static.talkxj.com/articles/7bf9fc73a83b29737f02ee0f9db547b7.png)

将上述三个文件传输到/usr/local/docker下（手动创建文件夹）

![QQ截图20210402212725.png](https://static.talkxj.com/articles/1617370924245.png)

## 5.docker运行后端项目

进入服务器/usr/local/docker下，构建后端镜像

```
sh ./blog-start.sh 

```

![QQ截图20210402213552.png](https://static.talkxj.com/articles/1617370970473.png)

**ps：第一次时间可能比较长，耐心等待即可**

查看是否构建成功

![QQ截图20210402214341.png](https://static.talkxj.com/articles/1617371042321.png)

可以去测试下接口是否运行成功

![Snipaste_20220905_043830.png](http://124.71.220.35:83/articles/4cdc3a0d75e86855832f22a0809e8c07.png)

**ps：需要重新部署只需重新传jar包，执行sh脚本即可**





## 6.打包前端项目

打开cmd，进入Vue项目路径 -> npm run build

![QQ截图20200706091427.png](https://static.talkxj.com/articles/1593998091709.png)

打包成功后会在目录下生成dist文件

![QQ截图20200706091817.png](https://static.talkxj.com/articles/1593998311729.png)

将Vue打包项目传输到/usr/local/vue下（由于我前台和后台分为两个项目，所以改名dist文件）

![QQ截图20200706092538.png](https://static.talkxj.com/articles/1593998758469.png)



## 7.nginx配置(无域名）

新建nginx.conf文件，将它放在宿主机的/usr/local/nginx文件夹中

```
events {
    worker_connections  1024;
}

http {
    include       mime.types;
    default_type  application/octet-stream;
    sendfile        on;
    keepalive_timeout  65;

    client_max_body_size     50m;
    client_body_buffer_size  10m; 
    client_header_timeout    1m;
    client_body_timeout      1m;

    gzip on;
    gzip_min_length  1k;
    gzip_buffers     4 16k;
    gzip_comp_level  4;
    gzip_types text/plain application/javascript application/x-javascript text/css application/xml text/javascript application/x-httpd-php image/jpeg image/gif image/png;
    gzip_vary on;

server {
        listen       80;
        server_name  你的ip;
     
        location / {		
            root   /usr/local/vue/blog;
            index  index.html index.htm; 
            try_files $uri $uri/ /index.html;	
        }
			
	location ^~ /api/ {		
            proxy_pass http://你的ip:8686/;
	    proxy_set_header   Host             $host;
            proxy_set_header   X-Real-IP        $remote_addr;						
            proxy_set_header   X-Forwarded-For  $proxy_add_x_forwarded_for;
        }
		
    }
	
server {
        listen       81;
        server_name  你的ip;
     
        location / {		
            root   /usr/local/vue/admin;
            index  index.html index.htm; 
            try_files $uri $uri/ /index.html;	
        }
			
	location ^~ /api/ {		
            proxy_pass http://你的ip:8686/;
	    proxy_set_header   Host             $host;
            proxy_set_header   X-Real-IP        $remote_addr;						
            proxy_set_header   X-Forwarded-For  $proxy_add_x_forwarded_for;
        }
		
    }

server {
        listen       82;
        server_name  你的ip;
     
        location / {
          proxy_pass http://你的ip:8686/websocket;
          proxy_http_version 1.1;
          proxy_set_header Upgrade $http_upgrade;
          proxy_set_header Connection "Upgrade";
          proxy_set_header Host $host:$server_port;
          proxy_set_header X-Real-IP $remote_addr;
          proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
          proxy_set_header X-Forwarded-Proto $scheme;
       }
	
    }

server {
        listen       83;
        server_name  你的ip;
     
        location / {		
          root /usr/local/upload/; 
        }		
		
    }
 
 }

```

docker启动nginx服务，挂载数据卷

```
docker run --name nginx --restart=always -p 80:80 -p 81:81 -p 82:82 -p 83:83 -d -v /usr/local/nginx/nginx.conf:/etc/nginx/nginx.conf -v /usr/local/vue:/usr/local/vue -v /usr/local/upload:/usr/local/upload nginx 

```







## 9.运行测试

去浏览器测试下是否运行成功

**ps：需要通过ip + 端口号访问项目**

前台：你的IP:80
![Snipaste_20220905_032736.png](http://124.71.220.35:83/articles/43e9d5746b276e0df3ce7988596561b3.png)

![Snipaste_20220905_044238.png](http://124.71.220.35:83/articles/72323b7f209338ad26989f93ba870989.png)![Snipaste_20220905_044247.png](http://124.71.220.35:83/articles/c349aeed8edf10ebe4b6ba23c4fb050d.png)![Snipaste_20220905_044255.png](http://124.71.220.35:83/articles/48409ac156ff2e522eff4af0f53efaa7.png)![Snipaste_20220905_044308.png](http://124.71.220.35:83/articles/98fd8413f67657f55bc6f86a40996852.png)


后台：你的IP:81
测试账号：test@qq.com，密码：123456
![Snipaste_20220905_032752.png](http://124.71.220.35:83/articles/b84e47f4529719898e932a51096905ae.png)

![Snipaste_20220905_044444.png](http://124.71.220.35:83/articles/6581a7027a57717f21afc5b4dd90597d.png)![Snipaste_20220905_044715.png](http://124.71.220.35:83/articles/4a21a1dfafbc9fec019aab80fd1d15f9.png)![Snipaste_20220905_044503.png](http://124.71.220.35:83/articles/cb44ddf49792b01a020bfa8981c88d0a.png)![Snipaste_20220905_044659.png](http://124.71.220.35:83/articles/842810a3ae436fe1a86e325c7b4042ff.png)



