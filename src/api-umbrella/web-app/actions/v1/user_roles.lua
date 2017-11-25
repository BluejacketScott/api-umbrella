local cjson = require "cjson"
local json_response = require "api-umbrella.web-app.utils.json_response"
local require_admin = require "api-umbrella.web-app.utils.require_admin"

local _M = {}

function _M.index(self)
  local response = {
    user_roles = {},
  }
  setmetatable(response["user_roles"], cjson.empty_array_mt)

  return json_response(self, response)
end

return function(app)
  app:get("/api-umbrella/v1/user_roles(.:format)", require_admin(_M.index))
end
