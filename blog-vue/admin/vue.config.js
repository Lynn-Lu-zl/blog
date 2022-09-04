module.exports = {
  productionSourceMap: false,
  devServer: {
      host: "124.71.220.35",//配置本项目运行主机
      port: 8081,//配置本项目运行端口
      proxy: {
          "/api": {
              // target: "http://localhost:8686",
              target: "http://124.71.220.35:8686",
              changeOrigin: true,
              pathRewrite: {
                  "^/api": ""
              }
          }
      },
    disableHostCheck: true
  },
  chainWebpack: config => {
    config.resolve.alias.set("@", resolve("src"));
  }
};

const path = require("path");
function resolve(dir) {
  return path.join(__dirname, dir);
}
