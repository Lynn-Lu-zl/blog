module.exports = {
  transpileDependencies: ["vuetify"],
  devServer: {
      host: "124.71.220.35",//配置本项目运行主机
      port: 80,//配置本项目运行端口
    proxy: {
      '/api': {
        // target: 'http://localhost:8686',
        target: 'http://124.71.220.35:8686',
        changeOrigin: true,
        pathRewrite: {
          '^/api': ''
        }
      }
    },
    disableHostCheck: true
  },
  productionSourceMap: false,
  css: {
    extract: true,
    sourceMap: false
  }
};
