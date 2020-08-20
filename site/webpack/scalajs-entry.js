if (process.env.NODE_ENV === "production") {
    const opt = require("./kafkamate-site-opt.js");
    opt.main();
    module.exports = opt;
} else {
    var exports = window;
    exports.require = require("./kafkamate-site-fastopt-entrypoint.js").require;
    window.global = window;

    const fastOpt = require("./kafkamate-site-fastopt.js");
    fastOpt.main()
    module.exports = fastOpt;

    if (module.hot) {
        module.hot.accept();
    }
}
