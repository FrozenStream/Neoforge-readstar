package git.frozenstream.readstar.elements;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import git.frozenstream.readstar.Config;
import git.frozenstream.readstar.ReadStar;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

import java.util.*;


/**
 * 为什么不设计纬度：纬度意味着昼夜长短不等，在minecraft原版设计中兼容性不佳
 * */

public class CelestialBodyManager {
    // 单例实例
    private static final CelestialBodyManager INSTANCE = new CelestialBodyManager();
    
    /**
     * 获取 CelestialBodyManager 单例实例
     * @return CelestialBodyManager 单例
     */
    public static CelestialBodyManager getInstance() {
        return INSTANCE;
    }
    
    private final Map<String, CelestialBody> celestialBodyTreeMap = new TreeMap<>();
    public final CelestialBody Root = CelestialBody.Root;
    
    // 私有构造函数，防止外部实例化
    private CelestialBodyManager() {
    }

    /**
     * 根据名称获取天体
     *
     * @param name 天体名称
     * @return 天体对象，不存在则返回 null
     */
    public CelestialBody getCelestialBody(String name) {
        return celestialBodyTreeMap.get(name);
    }

    /**
     * 获取所有天体列表
     *
     * @return 天体列表
     */
    public ArrayList<CelestialBody> getCelestialBodyTreeMap() {
        return new ArrayList<>(celestialBodyTreeMap.values());
    }
    /**
     * 判断有无指定名称的天体
     * @param name 天体名称
     * @return 是否有指定名称的天体
     */
    public boolean hasCelestialBody(String name) {
        return celestialBodyTreeMap.containsKey(name);
    }

    /**
     * 获取天体数量
     *
     * @return 天体总数
     */
    public int getCelestialBodyCount() {
        return celestialBodyTreeMap.size();
    }


    /**
     * 递归更新所有天体位置，委托给 CelestialBody.updatePosition(t)。
     * @param t 世界时间
     */
    public void updatePositions(long t) {
        Root.children.forEach(child -> child.updatePosition(t));
    }

    /**
     * 获取目标天体在观察者视野中的大小
     * @param observer 观察者
     * @param target 目标天体
     * @return 目标视大小
     */
    public float getApparentSize(Vector3f observer, CelestialBody target) {
        float distance = observer.distance(target.position);
        float k = (float) (target.radius / distance);
        float factor = Config.CELESTIAL_APPARENT_SIZE_FACTOR.get().floatValue();
        float minSize = Config.CELESTIAL_APPARENT_SIZE_MIN.get().floatValue();
        return Math.max(minSize, k * factor);
    }

    /**
     * 获取目标天体在观察者视野中被太阳光掩盖的程度
     * @param observer 观察者
     * @param target 目标天体
     * @return 目标被遮蔽导致的不透明度值
     */
    public float getCoveredBySun(Vector3f observer, CelestialBody target) {
        Vector3f obs_sun = (new Vector3f()).set(target.hostStar.position).sub(observer).normalize();
        Vector3f obs_tar = (new Vector3f()).set(target.position).sub(observer).normalize();
        return Mth.clamp(1 - obs_sun.dot(obs_tar),0.5f, 1f);
    }

    // JSON 配置键名常量
    private static final String KEY_SYSTEM = "System";
    private static final String KEY_MASS = "mass";
    private static final String KEY_RADIUS = "radius";
    private static final String KEY_LUMINANCE = "luminance";
    private static final String KEY_AXIS = "axis";
    private static final String KEY_ORBIT = "orbit";
    private static final String KEY_CHILDREN = "children";
    
    /**
     * 从 JSON 数据初始化天体系统
     * @param jsonData 包含天体系统配置的 JSON 对象
     */
    public void initializeFromJson(JsonObject jsonData) {
        ReadStar.LOGGER.info("CelestialBodyManager: Initializing celestial body system from JSON data");
        
        try {
            // 步骤1：清空现有数据
            celestialBodyTreeMap.clear();
            Root.children.clear();
            
            // 步骤2：获取 System 根节点
            JsonObject systemObj = jsonData.getAsJsonObject(KEY_SYSTEM);
            if (systemObj == null) {
                ReadStar.LOGGER.error("CelestialBodyManager: No 'System' object found in JSON data");
                return;
            }
            
            // 步骤3：递归解析所有顶层天体
            for (Map.Entry<String, JsonElement> entry : systemObj.entrySet()) {
                parseAndAddCelestialBody(entry.getKey(), entry.getValue().getAsJsonObject(), null);
            }

            celestialBodyTreeMap.forEach((name, celestialBody) -> {
                ReadStar.LOGGER.info("CelestialBodyManager: CelestialBody '{}' hoststar: {}", name, celestialBody.hostStar.name);
            });

            ReadStar.LOGGER.info("CelestialBodyManager: Successfully initialized {} celestial bodies from JSON", celestialBodyTreeMap.size());
        } catch (Exception e) {
            ReadStar.LOGGER.error("CelestialBodyManager: Failed to initialize celestial body system from JSON", e);
        }
    }
    
    /**
     * 递归解析并添加天体到系统中
     * @param name 天体名称
     * @param celestialBodyData 天体数据 JSON 对象
     * @param parent 父天体（可为 null，为 null 时父节点设为 Root）
     */
    private void parseAndAddCelestialBody(String name, JsonObject celestialBodyData, CelestialBody parent) {
        try {
            name = name.toLowerCase();
            // ========== 1. 解析天体属性 ==========
            double mass = celestialBodyData.get(KEY_MASS).getAsDouble();
            double radius = celestialBodyData.get(KEY_RADIUS).getAsDouble();
            int luminance = celestialBodyData.has(KEY_LUMINANCE) ? celestialBodyData.get(KEY_LUMINANCE).getAsInt() : 0;
            Vector3f axis = parseVector3f(celestialBodyData.getAsJsonArray(KEY_AXIS));
            Orbit orbit = parseOrbit(celestialBodyData.getAsJsonObject(KEY_ORBIT));
            
            // ========== 2. 创建天体对象 ==========
            ArrayList<CelestialBody> children = new ArrayList<>();
            CelestialBody celestialBody = new CelestialBody(name, mass, radius, luminance, axis, orbit, children);
            
            // ========== 3. 设置父子关系 ==========
            celestialBody.parent = (parent != null) ? parent : Root;
            
            // ========== 4. 注册到天体系统 ==========
            // 检查是否已存在
            if (celestialBodyTreeMap.containsKey(celestialBody.name)) {
                ReadStar.LOGGER.warn("CelestialBodyManager: CelestialBody '{}' already exists, replacing...", celestialBody.name);
            }

            // 添加到映射表
            celestialBodyTreeMap.put(celestialBody.name, celestialBody);

            // 添加到父节点的子列表
            if (!celestialBody.parent.children.contains(celestialBody)) {
                celestialBody.parent.children.add(celestialBody);
            }

            celestialBody.hostStar = CelestialBody.findLuminousAncestor(celestialBody);
            
            // ========== 5. 递归处理子天体 ==========
            JsonObject childrenData = celestialBodyData.getAsJsonObject(KEY_CHILDREN);
            if (childrenData != null) {
                for (Map.Entry<String, JsonElement> entry : childrenData.entrySet()) {
                    parseAndAddCelestialBody(entry.getKey(), entry.getValue().getAsJsonObject(), celestialBody);
                }
            }

            
            ReadStar.LOGGER.debug("CelestialBodyManager: Parsed celestial body '{}' with {} children", name, celestialBody.children.size());
        } catch (Exception e) {
            ReadStar.LOGGER.error("CelestialBodyManager: Failed to parse celestial body '{}'", name, e);
        }
    }
    
    /**
     * 从 JSON 数组解析 Vector3f
     * @param array JSON 数组 [x, y, z]
     * @return 解析后的向量
     */
    private Vector3f parseVector3f(JsonArray array) {
        if (array == null || array.size() < 3) {
            return new Vector3f(0, 0, -1);  // 默认值
        }
        return new Vector3f(
            array.get(0).getAsFloat(),
            array.get(1).getAsFloat(),
            array.get(2).getAsFloat()
        );
    }
    
    /**
     * 从 JSON 对象解析 Orbit
     * @param orbitData 轨道数据 JSON 对象
     * @return 解析后的轨道对象
     */
    private Orbit parseOrbit(JsonObject orbitData) {
        if (orbitData == null) {
            return new Orbit(0, 0, 0, 0, 0, 0);
        }
        
        return new Orbit(
            orbitData.has("semiMajorAxis") ? orbitData.get("semiMajorAxis").getAsDouble() : 0,
            orbitData.has("eccentricity") ? orbitData.get("eccentricity").getAsDouble() : 0,
            orbitData.has("inclination") ? orbitData.get("inclination").getAsDouble() : 0,
            orbitData.has("argumentOfPeriapsis") ? orbitData.get("argumentOfPeriapsis").getAsDouble() : 0,
            orbitData.has("longitudeOfAscendingNode") ? orbitData.get("longitudeOfAscendingNode").getAsDouble() : 0,
            orbitData.has("initialMeanAnomaly") ? orbitData.get("initialMeanAnomaly").getAsDouble() : 0
        );
    }
}
