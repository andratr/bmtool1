const host = process.env.BACKEND_HOST || 'backend-dev';
const port = process.env.BACKEND_PORT || '1000';
const target = `http://${host}:${port}`;
console.log('[proxy] target =', target);

module.exports = {
    '/api': {
        target,
        secure: false,
        changeOrigin: true,
        logLevel: 'debug',
        // if your backend routes are /query/** (not /api/**), uncomment:
        pathRewrite: { '^/api': '' },
    },
};
